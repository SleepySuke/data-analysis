package com.suke.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.suke.common.AnalysisResult;
import com.suke.common.Result;
import com.suke.context.UserContext;
import com.suke.domain.dto.chart.ChartAddDTO;
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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addChart(ChartAddDTO chartAddDTO) {
        log.info("添加的图表信息：{}", chartAddDTO);
        if(chartAddDTO == null){
            log.error("添加图表信息参数错误");
            return null;
        }
        Long userId = UserContext.getCurrentId();
        log.info("用户id：{}",userId);
        if(userId == null){
            log.error("用户未登录");
            return null;
        }
        Chart chart = new Chart();
        BeanUtil.copyProperties(chartAddDTO,chart);
        chart.setUserId(userId);
        boolean save = this.save(chart);
        if(!save){
            log.error("添加图表信息失败");
            return null;
        }
        return chart.getId();
    }

    @Override
    public Chart getChartById(Long Id) {
        log.info("获取图表信息：{}",Id);
        Chart chart = this.getById(Id);
        if(chart != null){
            return chart;
        }
        return null;
    }

    @Override
    public GenChartVO analysisFile(MultipartFile multipartFile, UploadFileDTO fileDTO) {
        log.info("文件描述：{}",fileDTO);
        log.info("文件：{}",multipartFile);
        Long userId = UserContext.getCurrentId();
        if(userId == null){
            log.error("用户未登录");
            return null;
        }
        String fileName = fileDTO.getFileName();
        String goal = fileDTO.getGoal();
        String chartType = fileDTO.getChartType();
        if(StringUtils.isAnyBlank(fileName)){
            log.error("文件名称过长:{}", fileName);
            throw new BaseException("文件名称过长");
        }
        if(StringUtils.isAnyBlank( goal)){
            log.error("分析目标为空:{}", goal);
            throw new BaseException("分析目标为空");
        }
        if(StringUtils.isAnyBlank(chartType)){
            log.error("图表类型为空:{}", chartType);
            throw new BaseException("图表类型为空");
        }
        String csvData = FileUtils.excelToCsv(multipartFile);
        if(StringUtils.isAnyBlank(csvData)){
            log.error("数据为空");
            throw new BaseException("数据为空");
        }
        String result = aiDocking.doDataAnalysis(goal,chartType,csvData);
        log.info("分析结果:{}", result);
        AnalysisResult analysisResult = ParseAIResponse.parseResponse(result);
        Chart chart = new Chart();
        chart.setName(fileName)
                .setGoal( goal)
                .setChartType(chartType)
                .setChartData(csvData)
                .setGenChart(analysisResult.getChartConfig())//图表配置
                .setGenResult(analysisResult.getAnalysis())//图表结果
                .setUserId(userId);
        log.info("保存的图表信息：{}",chart);
        boolean save = this.save(chart);
        if(!save){
            throw new FailSaveException("保存图表信息失败");
        }
        GenChartVO genChartVO = new GenChartVO();
        genChartVO.setChartId(chart.getId())
                .setGenChart(chart.getGenChart())
                .setGenResult(chart.getGenResult());
        return genChartVO;
    }

}
