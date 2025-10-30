package com.suke.controller;


import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.domain.dto.chart.ChartAddDTO;
import com.suke.domain.dto.file.UploadFileDTO;
import com.suke.domain.entity.Chart;
import com.suke.domain.vo.GenChartVO;
import com.suke.service.IChartService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;




/**
 * <p>
 * 图表信息表 前端控制器
 * </p>
 *
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private IChartService chartService;

    /**
     * 添加图表信息
     * @param chartAddDTO
     * @return
     */
    @PostMapping("/add")
    public Result<Long> addChart(@RequestBody ChartAddDTO chartAddDTO){
        log.info("添加图表信息：{}",chartAddDTO);
        if(chartAddDTO == null){
            log.error("添加图表信息参数错误");
            return Result.error(ErrorCode.PARAMS_ERROR.getMessage());
        }
        Long id = chartService.addChart(chartAddDTO);
        return Result.success(id);
    }

    /**
     * 获取图表信息
     * @param Id
     * @return
     */
    @GetMapping("/getChart")
    public Result<Chart> getChart(Long Id){
        log.info("获取图表信息的id：{}",Id);
        if( Id == null || Id < 0){
            log.error("获取图表信息参数错误");
            return Result.error(ErrorCode.PARAMS_ERROR.getMessage());
        }
        Chart chartVO = chartService.getChartById(Id);
        if(chartVO == null){
            log.error("获取图表信息失败");
            return Result.error(ErrorCode.OPERATION_ERROR.getMessage());
        }
        log.info("获取图表信息成功：{}",chartVO);
        return Result.success(chartVO);
    }

    /**
     * 上传图表信息智能分析
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    @PostMapping("/gen")
    public Result<GenChartVO> uploadChart(@RequestPart("file")MultipartFile multipartFile, UploadFileDTO fileDTO){
        log.info("文件描述：{}",fileDTO);
        log.info("上传的文件：{}",multipartFile);
        if(fileDTO == null){
            log.error("上传图表信息参数错误");
            return Result.error(ErrorCode.PARAMS_ERROR.getMessage());
        }
        String fileName = fileDTO.getFileName();
        String goal = fileDTO.getGoal();
        String chartType = fileDTO.getChartType();
        if(StringUtils.isAnyBlank(goal)){
            log.error("分析目标为空:{}", goal);
            return Result.error("分析目标为空");
        }
        if(StringUtils.isNotBlank(fileName) && fileName.length() > 100){
            log.error("文件名称过长:{}", fileName);
            return Result.error("文件名称过长");
        }
        if(StringUtils.isAnyBlank(chartType)){
            log.error("图表类型为空:{}", chartType);
            return Result.error("图表类型为空");
        }
        GenChartVO genChartVO = chartService.analysisFile(multipartFile, fileDTO);
        return Result.success(genChartVO);
    }
}
