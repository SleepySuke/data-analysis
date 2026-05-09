package com.suke.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.rholder.retry.Retryer;
import com.suke.common.AnalysisResult;
import com.suke.common.WebSocketMessage;
import com.suke.config.RetryConfig;
import com.suke.context.UserContext;
import com.suke.datamq.MessageConsumer;
import com.suke.datamq.MessageProducer;
import com.suke.domain.dto.chart.ChartAddDTO;
import com.suke.domain.dto.chart.ChartEditDTO;
import com.suke.domain.dto.chart.ChartPageQueryDTO;
import com.suke.domain.dto.file.SmartFileResult;
import com.suke.domain.dto.file.UploadFileDTO;
import com.suke.domain.entity.Chart;
import com.suke.domain.vo.GenChartVO;
import com.suke.exception.AIDockingException;
import com.suke.exception.BaseException;
import com.suke.exception.FailSaveException;
import com.suke.mapper.ChartMapper;
import com.suke.service.IChartService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suke.service.IUserService;
import com.suke.utils.*;
import io.netty.handler.timeout.TimeoutException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 图表信息表 服务实现类
 * </p>
 *
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
@Service
@Slf4j
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart> implements IChartService {

    @Resource
    private AIDocking aiDocking;
    @Autowired
    private ChartMapper chartMapper;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    @Qualifier("aiAnalyzeRetryer")
    private Retryer<String> aiAnalyzeRetryer;

    @Autowired
    @Qualifier("syncAnalyzeRetryer")
    private Retryer<String> syncAnalyzeRetryer;

    @Resource
    private MessageProducer messageProducer;

    @Autowired
    private MinioUtil minioUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addChart(ChartAddDTO chartAddDTO) {
        log.info("添加的图表信息：{}", chartAddDTO);
        if (chartAddDTO == null) {
            log.error("添加图表信息参数错误");
            return null;
        }
        Long userId = UserContext.getCurrentId();
        log.info("用户id：{}", userId);
        if (userId == null) {
            log.error("用户未登录");
            return null;
        }
        Chart chart = new Chart();
        BeanUtil.copyProperties(chartAddDTO, chart);
        chart.setUserId(userId);
        boolean save = this.save(chart);
        if (!save) {
            log.error("添加图表信息失败");
            return null;
        }
        return chart.getId();
    }

    @Override
    public Chart getChartById(Long Id) {
        log.info("获取图表信息：{}", Id);
        Chart chart = this.getById(Id);
        if (chart != null) {
            return chart;
        }
        return null;
    }

    /**
     * 同步分析文件
     *
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    @Override
    public GenChartVO analysisFile(MultipartFile multipartFile, UploadFileDTO fileDTO) {
        log.info("文件描述：{}", fileDTO);
        log.info("文件：{}", multipartFile);
        if (multipartFile == null) {
            log.error("文件为空");
            return null;
        }
        if (!FileUtils.validSuffix(multipartFile)) {
            log.error("文件格式错误");
            return null;
        }

        //限制文件大小为100mb
        long maxSizeMB = 100;
        if (!FileUtils.validFileSize(multipartFile, maxSizeMB)) {
            log.error("文件过大，最大支持{}MB", maxSizeMB);
            return null;
        }
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            log.error("用户未登录");
            return null;
        }
        String fileName = fileDTO.getFileName();
        String goal = fileDTO.getGoal();
        String chartType = fileDTO.getChartType();
        if (StringUtils.isAnyBlank(fileName)) {
            log.error("文件名称过长:{}", fileName);
            throw new BaseException("文件名称过长");
        }
        if (StringUtils.isAnyBlank(goal)) {
            log.error("分析目标为空:{}", goal);
            throw new BaseException("分析目标为空");
        }
        if (StringUtils.isAnyBlank(chartType)) {
            log.error("图表类型为空:{}", chartType);
            throw new BaseException("图表类型为空");
        }

        String csv = null;
        String minioPath = null;
        int sampleRows = 2000;
        //对于小文件10mb可以直接处理
        long fileSizeMB = multipartFile.getSize() / (1024 * 1024);
        log.info("文件大小:{}MB", multipartFile.getSize() / (1024 * 1024));
        if(multipartFile.getSize() < 1024 * 1024 * 10){
            csv = FileUtils.excelToCsv(multipartFile);
            //检测转换为csv文件后的大小，如果大于1mb则上传至minio中
            if(csv.getBytes().length > 1024 * 1024){
                //上传至minio中，通过时间戳进行识别
                String csvFileName = "csv/" + userId + "/" + System.currentTimeMillis() + ".csv";
                minioPath = minioUtil.uploadCsvFile(csv, csvFileName);
            }
        }else{
            log.info("文件过大，开始进行采样处理->{}MB", multipartFile.getSize() / (1024 * 1024));
            try{
                //默认的采用处理
                if(fileDTO.getEnableSampling() != null && fileDTO.getEnableSampling() && fileDTO.getSampleRows() > 0){
                    sampleRows = fileDTO.getSampleRows();
                    csv = FileUtils.smartSampling(multipartFile, minioUtil, sampleRows);
                    log.info("采样处理{}行的结果:{}",sampleRows, csv);
                }else{
                    if(fileSizeMB > 50){
                        sampleRows = 1000; // 超大文件采样1000行
                    } else if(fileSizeMB > 20){
                        sampleRows = 2000; // 大文件采样2000行
                    } else {
                        sampleRows = 3000; // 中等文件采样3000行
                    }
                    SmartFileResult result = FileUtils.smartProcessLargeFile(
                            multipartFile,
                            minioUtil,
                            sampleRows,
                            true // 启用流式处理到MinIO
                    );

                    csv = result.getSampledData();
                    minioPath = result.getOriginalMinioPath();

                    // 如果采样数据也很大，使用采样数据的MinIO路径
                    if(StringUtils.isNotBlank(result.getSampledMinioPath())){
                        minioPath = result.getSampledMinioPath();
                    }

                    log.info("大文件智能处理完成，总行数: {}, 采样行数: {}",
                            result.getTotalRows(), FileUtils.countLines(csv));
//                    //先上传至minio再进行处理
//                    String originFileName = "original/" + userId + "/" + System.currentTimeMillis() + "_" + multipartFile.getOriginalFilename();
//                    minioPath = minioUtil.uploadFile(multipartFile, originFileName);
//                    //流式处理转为csv并且存到minio
//                    String csvFileName =FileUtils.excelToCsvWithStream(multipartFile, minioUtil, 1000);
//                    csv = FileUtils.smartSampling(multipartFile, minioUtil, 1000);
//                    minioPath = csvFileName;
                }
            }catch (Exception e){
                log.error("文件处理失败");
                throw new BaseException("文件处理失败");
            }
        }
        int length = csv.getBytes().length;
        log.info("文件大小->{}", length);
        if (StringUtils.isAnyBlank(csv)) {
            log.error("数据为空");
            throw new BaseException("数据为空");
        }

        //估计token数量
        int estimatedTokens = estimateTokens(csv, goal, chartType);
        log.info("估计token数量: {}", estimatedTokens);

        if(estimatedTokens > 5000){
            log.warn("使用token数量过大 -> {}", estimatedTokens);
        }


        String result = null;
        try {
            String res = csv;
            // 使用重试器调用AI分析
            result = syncAnalyzeRetryer.call(() -> {
                try {
                    log.info("调用AI进行分析，数据大小: {} 字符", res.length());
                    return aiDocking.doDataAnalysis(goal, chartType, res);
                } catch (Exception e) {
                    log.error("AI分析调用失败: {}", e.getMessage());

                    // 检查是否为token超限，如果是，抛出特定异常让重试器知道不重试
                    if (e.getMessage() != null &&
                            (e.getMessage().contains("token") ||
                                    e.getMessage().contains("Token"))) {
                        throw new AIDockingException("数据量过大导致token超限，建议启用采样功能或减少采样行数");
                    }

                    // 检查是否为参数错误，如果是，抛出异常让重试器知道不重试
                    if (e.getMessage() != null &&
                            e.getMessage().contains("参数错误")) {
                        throw new RuntimeException("参数错误: " + e.getMessage());
                    }

                    // 其他异常直接抛出，重试器会根据异常类型决定是否重试
                    throw e;
                }
            });
            log.info("分析结果:{}", result);
        } catch (Exception e) {
            log.error("AI分析失败 token超限", e);
            // 如果是Token超限，建议用户启用采样
            if (e.getMessage().contains("token") || e.getMessage().contains("Token")) {
                return null;
            }
            return null;
        }
        AnalysisResult analysisResult = ParseAIResponse.parseResponse(result);
        Chart chart = new Chart();
        chart.setName(fileName)
                .setGoal(goal)
                .setChartType(chartType)
                .setChartData(csv)
                .setGenChart(analysisResult.getChartConfig())//图表配置
                .setGenResult(analysisResult.getAnalysis())//图表结果
                .setUserId(userId)
                .setStatus("succeed")
                .setExecMsg("分析成功");
        ;
        log.info("保存的图表信息：{}", chart);

        //csv数据文件过大时存放至minio中，返回路径
        if(StringUtils.isNotBlank(minioPath)){
            chart.setMinioPath(minioPath);
            chart.setChartData("文件数据过大，才minio中");
        }

        boolean save = this.save(chart);
        if (!save) {
            throw new FailSaveException("保存图表信息失败");
        }
        GenChartVO genChartVO = new GenChartVO();
        genChartVO.setChartId(chart.getId())
                .setGenChart(chart.getGenChart())
                .setGenResult(chart.getGenResult());
        return genChartVO;
    }

    /**
     * 异步分析文件
     *
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public GenChartVO asyncAnalyzeFile(MultipartFile multipartFile, UploadFileDTO fileDTO) {
        log.info("文件描述：{}", fileDTO);
        log.info("文件：{}", multipartFile);
        if (multipartFile == null) {
            log.error("文件为空");
            return null;
        }
        if (!FileUtils.validSuffix(multipartFile)) {
            log.error("文件格式错误");
            return null;
        }
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            log.error("用户未登录");
            return null;
        }
        String fileName = fileDTO.getFileName();
        String goal = fileDTO.getGoal();
        String chartType = fileDTO.getChartType();
        if (StringUtils.isAnyBlank(fileName)) {
            log.error("文件名称过长:{}", fileName);
            throw new BaseException("文件名称过长");
        }
        if (StringUtils.isAnyBlank(goal)) {
            log.error("分析目标为空:{}", goal);
            throw new BaseException("分析目标为空");
        }
        if (StringUtils.isAnyBlank(chartType)) {
            log.error("图表类型为空:{}", chartType);
            throw new BaseException("图表类型为空");
        }
        String csvData = FileUtils.excelToCsv(multipartFile);
        if (StringUtils.isAnyBlank(csvData)) {
            log.error("数据为空");
            throw new BaseException("数据为空");
        }
        //先保存数据，再去调用AI分析，此时AI调用变为异步调用，保存数据改为同步调用，AI调用为提交任务
        Chart chart = new Chart();
        //分析结果还未生成，仅保存图表名称等简单参数
        chart.setName(fileName)
                .setChartType(chartType)
                .setChartData(csvData)
                .setGoal(goal);
        chart.setStatus("wait");
        chart.setUserId(userId);
        boolean save = this.save(chart);
        if (!save) {
            log.error("保存图表信息失败");
            throw new FailSaveException("保存图表信息失败");
        }
        log.info("保存的图表的id：{}", chart.getId());
        Long asyncUserId = userId;
        Long asyncChartId = chart.getId();
        webSocketServer.sendMessageToUser(asyncUserId.toString(), buildWsMsg("analysis_status", "正在等待处理数据", "waiting", asyncChartId));
        //异步处理数据,提交任务
        try {
            CompletableFuture.runAsync(() -> {
                UserContext.setCurrentId(asyncUserId);
                try {
                    log.info("开始处理数据");
                    webSocketServer.sendMessageToUser(asyncUserId.toString(), buildWsMsg("analysis_status", "正在处理数据", "running", asyncChartId));
                    //此时处理数据，需要将状态进行修改
                    Chart updateChart = new Chart();
                    updateChart.setId(chart.getId());
                    updateChart.setStatus("running");
                    boolean update = this.updateById(updateChart);
                    if (!update) {
                        log.error("更新图表信息失败");
                        return;
                    }
                    //对于提交的任务设置一个超时机制
                    String result = aiAnalyzeRetryer.call(() -> {
                        CompletableFuture<String> aiFuture = CompletableFuture.supplyAsync(() ->
                                        aiDocking.doDataAnalysis(goal, chartType, csvData)
                                , threadPoolExecutor);
                        try {
                            return aiFuture.get(5, TimeUnit.MINUTES);
                        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                            log.error("AI处理异常: {}", e.getMessage());
                            throw new RuntimeException(e);
                        }
                    });
                    //状态修改完毕后，调用AI进行数据处理
//                    String result = aiDocking.doDataAnalysis(goal, chartType, csvData);
                    AnalysisResult analysisResult = ParseAIResponse.parseResponse(result);
                    Chart resChart = new Chart();
                    resChart.setId(asyncChartId);
                    resChart.setStatus("succeed");
                    resChart.setGenChart(analysisResult.getChartConfig());
                    resChart.setGenResult(analysisResult.getAnalysis());
                    resChart.setExecMsg("分析成功");
                    boolean resUpdate = this.updateById(resChart);
                    if (!resUpdate) {
                        log.error("更新图表信息失败");
                    }
                    log.info("更新图表信息成功");
                    Map<String, Object> resultData = new HashMap<>();
                    resultData.put("genChart", analysisResult.getChartConfig());
                    resultData.put("genResult", analysisResult.getAnalysis());
                    webSocketServer.sendMessageToUser(asyncUserId.toString(), buildWsMsg("analysis_status", "分析成功", "succeed", asyncChartId));
                } catch (Exception e) {
                    log.error("分析任务执行异常，图表ID: {}", asyncChartId, e);
                    Chart errorChart = new Chart();
                    errorChart.setId(asyncChartId);
                    errorChart.setStatus("failed");
                    errorChart.setExecMsg("分析失败: " + e.getMessage());
                    this.updateById(errorChart);
                    webSocketServer.sendMessageToUser(asyncUserId.toString(), buildWsMsg("analysis_status", "分析失败", "failed", asyncChartId));
                } finally {
                    UserContext.removeCurrentId();
                }
            }, threadPoolExecutor);
        } catch (RejectedExecutionException e) {
            log.error("任务提交被拒绝，系统繁忙，图表ID: {}", asyncChartId);
            Chart rejectedChart = new Chart();
            rejectedChart.setId(asyncChartId);
            rejectedChart.setStatus("failed");
            rejectedChart.setExecMsg("系统繁忙，请稍后再试");
            this.updateById(rejectedChart);
            webSocketServer.sendMessageToUser(asyncUserId.toString(), buildWsMsg("analysis_status", "系统繁忙，请稍后再试", "failed", asyncChartId));
        }
        GenChartVO genChartVO = new GenChartVO();
        genChartVO.setChartId(chart.getId());
        genChartVO.setGenResult(chart.getGenResult());
        genChartVO.setGenChart(chart.getGenChart());
        return genChartVO;
    }

    /**
     * mq异步分析文件,为了防止任务的丢失，在分布式情况下，需要考虑任务丢失问题
     *
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public GenChartVO asyncAnalyze(MultipartFile multipartFile, UploadFileDTO fileDTO) {
        log.info("文件描述：{}", fileDTO);
        log.info("文件：{}", multipartFile);
        if (multipartFile == null) {
            log.error("文件为空");
            return null;
        }
        if (!FileUtils.validSuffix(multipartFile)) {
            log.error("文件格式错误");
            return null;
        }
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            log.error("用户未登录");
            return null;
        }
        String fileName = fileDTO.getFileName();
        String goal = fileDTO.getGoal();
        String chartType = fileDTO.getChartType();
        if (StringUtils.isAnyBlank(fileName)) {
            log.error("文件名称过长:{}", fileName);
            throw new BaseException("文件名称过长");
        }
        if (StringUtils.isAnyBlank(goal)) {
            log.error("分析目标为空:{}", goal);
            throw new BaseException("分析目标为空");
        }
        if (StringUtils.isAnyBlank(chartType)) {
            log.error("图表类型为空:{}", chartType);
            throw new BaseException("图表类型为空");
        }
        String csvData = FileUtils.excelToCsv(multipartFile);
        if (StringUtils.isAnyBlank(csvData)) {
            log.error("数据为空");
            throw new BaseException("数据为空");
        }
        //先保存数据，再去调用AI分析，此时AI调用变为异步调用，保存数据改为同步调用，AI调用为提交任务
        Chart chart = new Chart();
        //分析结果还未生成，仅保存图表名称等简单参数
        chart.setName(fileName)
                .setChartType(chartType)
                .setChartData(csvData)
                .setGoal(goal);
        chart.setStatus("wait");
        chart.setUserId(userId);
        boolean save = save(chart);
        if (!save) {
            log.error("保存图表失败");
            return null;
        }
        Long chartId = chart.getId();
        // 发送消息
        messageProducer.sendMessage(String.valueOf(chartId));
        GenChartVO genChartVO = new GenChartVO();
        genChartVO.setChartId(chart.getId());
        return genChartVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Page<Chart> getMyChartList(ChartPageQueryDTO chartPageQueryDTO) {
        log.info("获取我的图表列表：{}", chartPageQueryDTO);
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            log.error("用户未登录");
            return null;
        }
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.like(StringUtils.isNotBlank(chartPageQueryDTO.getName()), "name", chartPageQueryDTO.getName());
        queryWrapper.orderByDesc("create_time");
        return this.page(new Page<>(chartPageQueryDTO.getPage(), chartPageQueryDTO.getPageSize()), queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean editChart(ChartEditDTO chartEditDTO) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            log.error("用户未登录");
            return false;
        }
        Chart chart = this.getById(chartEditDTO.getId());
        if (chart == null) {
            log.error("图表信息不存在");
            return false;
        }
        if (!chart.getUserId().equals(userId)) {
            log.error("用户没有权限修改该图表信息");
            return false;
        }
        //此时图表的配置也需要重新进行更改，因为图表的类型会发生变化
        if (!chart.getChartType().equals(chartEditDTO.getChartType())) {
            try {
                String res = aiDocking.doDataAnalysis(chartEditDTO.getGoal(), chartEditDTO.getChartType(), chart.getChartData());
                AnalysisResult analysisResult = ParseAIResponse.parseResponse(res);
                chart.setGenChart(analysisResult.getChartConfig())
                        .setGenResult(analysisResult.getAnalysis());
            } catch (Exception e) {
                log.error("图表配置错误,保留原配置");
                throw new AIDockingException("图表配置错误");
            }
        }
        chart.setChartType(chartEditDTO.getChartType())
                .setName(chartEditDTO.getName())
                .setGoal(chartEditDTO.getGoal());
        return this.updateById(chart);
    }

    /**
     * 估计token数量
     */
    private int estimateTokens(String csv, String goal, String chartType) {
        // 简单估算：中文大约1个字符1-2个token
        int csvTokens = csv.length() * 2;
        int goalTokens = goal.length() * 2;
        int chartTypeTokens = chartType.length() * 2;

        // 加上提示词模板的大概token数（约500）
        return csvTokens + goalTokens + chartTypeTokens + 500;
    }

    private WebSocketMessage buildWsMsg(String type, String message, String status, Long chartId) {
        Map<String, Object> data = new HashMap<>();
        data.put("chartId", chartId);
        return new WebSocketMessage()
                .setType(type)
                .setMessage(message)
                .setStatus(status)
                .setData(data);
    }

}
