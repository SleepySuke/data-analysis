package com.suke.controller;


import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.domain.dto.chart.ChartAddDTO;
import com.suke.domain.entity.Chart;
import com.suke.service.IChartService;
import com.suke.service.IUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

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
@Api(tags = "图表信息表")
public class ChartController {

    @Resource
    private IChartService chartService;

    /**
     * 添加图表信息
     * @param chartAddDTO
     * @return
     */
    @PostMapping("/add")
    @ApiOperation("添加图表信息")
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
    @ApiOperation("获取图表信息")
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
}
