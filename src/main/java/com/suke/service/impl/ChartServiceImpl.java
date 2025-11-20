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
import com.suke.utils.AIDocking;
import com.suke.utils.FileUtils;
import com.suke.utils.ParseAIResponse;
import com.suke.utils.WebSocketServer;
import io.netty.handler.timeout.TimeoutException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private Retryer<String> aiAnalyzeRetryer;

    @Resource
    private MessageProducer messageProducer;

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
        String result = aiDocking.doDataAnalysis(goal, chartType, csvData);
        log.info("分析结果:{}", result);
        AnalysisResult analysisResult = ParseAIResponse.parseResponse(result);
        Chart chart = new Chart();
        chart.setName(fileName)
                .setGoal(goal)
                .setChartType(chartType)
                .setChartData(csvData)
                .setGenChart(analysisResult.getChartConfig())//图表配置
                .setGenResult(analysisResult.getAnalysis())//图表结果
                .setUserId(userId)
                .setStatus("succeed")
                .setExecMsg("分析成功");
        ;
        log.info("保存的图表信息：{}", chart);
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
        WebSocketMessage wsMsg = new WebSocketMessage();
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
        Map<String, Object> waitingMsg = new HashMap<>();
        waitingMsg.put("chartId", chart.getId());
        wsMsg.setType("analysis_status")
                .setMessage("正在等待处理数据")
                .setStatus("waiting")
                .setData(waitingMsg);
        webSocketServer.sendMessageToUser(userId.toString(), wsMsg);
        //异步处理数据,提交任务
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("开始处理数据");
                    Map<String, Object> runningMsg = new HashMap<>();
                    runningMsg.put("chartId", chart.getId());
                    wsMsg.setType("analysis_status")
                            .setMessage("正在处理数据")
                            .setStatus("running")
                            .setData(runningMsg);
                    webSocketServer.sendMessageToUser(userId.toString(), wsMsg);
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
                    CompletableFuture<String> aiFuture = CompletableFuture.supplyAsync(() ->
                                    aiDocking.doDataAnalysis(goal, chartType, csvData)
                            , threadPoolExecutor);
                    aiFuture.orTimeout(5, TimeUnit.MINUTES);
                    String result = aiAnalyzeRetryer.call(() -> {
                        try {
                            return aiFuture.get();
                        } catch (InterruptedException | ExecutionException e) {
                            log.error("AI处理超时");
                        }
                        return null;
                    });
                    //状态修改完毕后，调用AI进行数据处理
//                    String result = aiDocking.doDataAnalysis(goal, chartType, csvData);
                    AnalysisResult analysisResult = ParseAIResponse.parseResponse(result);
                    Chart resChart = new Chart();
                    resChart.setId(chart.getId());
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
                    wsMsg.setType("analysis_status")
                            .setMessage("分析成功")
                            .setStatus("succeed")
                            .setData(resultData);
                    webSocketServer.sendMessageToUser(userId.toString(), wsMsg);
                } catch (TimeoutException e) {
                    log.error("任务超时，图表ID: {}", chart.getId());
                    // 立即更新状态为失败
                    Chart timeoutChart = new Chart();
                    timeoutChart.setId(chart.getId());
                    timeoutChart.setStatus("failed");
                    timeoutChart.setExecMsg("任务超时");
                    this.updateById(timeoutChart);
                    Map<String, Object> timeoutData = new HashMap<>();
                    timeoutData.put("chartId", chart.getId());
                    wsMsg.setType("analysis_status")
                            .setMessage("任务超时")
                            .setStatus("failed")
                            .setData(timeoutData);
                    webSocketServer.sendMessageToUser(userId.toString(), wsMsg);
                } catch (Exception e) {
                    log.error("分析任务执行异常，图表ID: {}", chart.getId(), e);
                    // 更新失败状态
                    Chart errorChart = new Chart();
                    errorChart.setId(chart.getId());
                    errorChart.setStatus("failed");
                    errorChart.setExecMsg("分析失败: " + e.getMessage());
                    this.updateById(errorChart);
                    Map<String, Object> errorData = new HashMap<>();
                    errorData.put("chartId", chart.getId());
                    wsMsg.setType("analysis_status")
                            .setMessage("分析失败")
                            .setStatus("failed")
                            .setData(errorData);
                    webSocketServer.sendMessageToUser(userId.toString(), wsMsg);
                }
            }, threadPoolExecutor);
        } catch (RejectedExecutionException e) {
            log.error("任务提交被拒绝，系统繁忙，图表ID: {}", chart.getId());
            // 立即更新状态为失败
            Chart rejectedChart = new Chart();
            rejectedChart.setId(chart.getId());
            rejectedChart.setStatus("failed");
            rejectedChart.setExecMsg("系统繁忙，请稍后再试");
            this.updateById(rejectedChart);
            Map<String, Object> rejectedData = new HashMap<>();
            rejectedData.put("chartId", chart.getId());
            wsMsg.setType("analysis_status")
                    .setMessage("系统繁忙，请稍后再试")
                    .setStatus("failed")
                    .setData(rejectedData);
            webSocketServer.sendMessageToUser(userId.toString(), wsMsg);
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
        queryWrapper.eq("userId", userId);
        queryWrapper.like(StringUtils.isNotBlank(chartPageQueryDTO.getName()), "name", chartPageQueryDTO.getName());
        queryWrapper.orderByDesc("createTime");
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

}
