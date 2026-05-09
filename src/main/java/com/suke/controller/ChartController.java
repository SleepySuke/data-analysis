package com.suke.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.suke.annotation.AuthCheck;
import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.context.UserContext;
import com.suke.domain.dto.chart.ChartAddDTO;
import com.suke.domain.dto.chart.ChartEditDTO;
import com.suke.domain.dto.chart.ChartPageQueryDTO;
import com.suke.domain.dto.file.UploadFileDTO;
import com.suke.domain.entity.Chart;
import com.suke.domain.vo.GenChartVO;
import com.suke.service.IChartService;
import com.suke.utils.RedisUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private RedisUtils redisUtils;

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
        Long currentUserId = UserContext.getCurrentId();
        if (!chartVO.getUserId().equals(currentUserId)) {
            log.error("用户无权限访问该图表");
            return Result.error(ErrorCode.NO_AUTH_ERROR.getMessage());
        }
        log.info("获取图表信息成功：{}",chartVO);
        return Result.success(chartVO);
    }

    /**
     * 返回用户编辑图表
     * @param Id
     * @return
     */
    @GetMapping("/getChartEdit")
    public Result<Chart> editChart(Long Id){
        log.info("获取要修改图表信息的id：{}",Id);
        if( Id == null || Id < 0){
            log.error("图表信息参数错误");
            return Result.error(ErrorCode.PARAMS_ERROR.getMessage());
        }
        Chart chartVO = chartService.getChartById(Id);
        if(chartVO == null){
            log.error("查询图表失败");
            return Result.error(ErrorCode.OPERATION_ERROR.getMessage());
        }
        Long currentUserId = UserContext.getCurrentId();
        if (!chartVO.getUserId().equals(currentUserId)) {
            log.error("用户无权限编辑该图表");
            return Result.error(ErrorCode.NO_AUTH_ERROR.getMessage());
        }
        return Result.success(chartVO);
    }

    /**
     * 同步上传图表信息智能分析
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    @AuthCheck
    @PostMapping("/gen")
    public Result<GenChartVO> uploadChart(@RequestPart("file")MultipartFile multipartFile, UploadFileDTO fileDTO){
        log.info("文件描述：{}",fileDTO);
        log.info("上传的文件：{}",multipartFile);
        Long userId = UserContext.getCurrentId();
        // 对用户限流
        redisUtils.doRateLimit(userId.toString());
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

    /**
     * 异步生成图表信息
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    @AuthCheck
    @PostMapping("/gen/async")
    public Result<GenChartVO> genAsync(@RequestPart("file")MultipartFile multipartFile, UploadFileDTO fileDTO){
        log.info("文件描述：{}",fileDTO);
        log.info("上传的文件：{}",multipartFile);
        Long userId = UserContext.getCurrentId();
        // 对用户限流
        redisUtils.doRateLimit(userId.toString());
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
        GenChartVO genChartVO = chartService.asyncAnalyzeFile(multipartFile, fileDTO);
        return Result.success(genChartVO);
    }

    /**
     * 用于分布式情况下的异步生成图表信息
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    @AuthCheck
    @PostMapping("/gen/async/mq")
    public Result<GenChartVO> genAsyncMq(@RequestPart("file")MultipartFile multipartFile, UploadFileDTO fileDTO){
        log.info("文件描述：{}",fileDTO);
        log.info("上传的文件：{}",multipartFile);
        Long userId = UserContext.getCurrentId();
        // 对用户限流
        redisUtils.doRateLimit(userId.toString());
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
        GenChartVO genChartVO = chartService.asyncAnalyze(multipartFile, fileDTO);
        return Result.success(genChartVO);
    }

    /**
     * 获取我的图表列表
     * @param chartPageQueryDTO
     * @return
     */
    @AuthCheck
    @PostMapping("/my/list/page")
    public Result<Page<Chart>> getMyChartList(@RequestBody ChartPageQueryDTO chartPageQueryDTO){
        log.info("获取我的图表列表：{}",chartPageQueryDTO);
        if(chartPageQueryDTO == null){
            log.error("获取我的图表列表参数错误");
            return Result.error(ErrorCode.PARAMS_ERROR.getMessage());
        }
        Page<Chart> pageResult = chartService.getMyChartList(chartPageQueryDTO);
        log.info("获取我的图表列表成功：{}",pageResult);
        return Result.success(pageResult);
    }

    /**
     * 修改我的图表信息
     * @param chartEditDTO
     * @return
     */
    @AuthCheck
    @PostMapping("/editChart")
    public Result editMyChart(@RequestBody ChartEditDTO chartEditDTO){
        log.info("修改我的图表信息：{}",chartEditDTO);
        if(chartEditDTO == null){
            log.error("修改我的图表信息参数错误");
            return Result.error(ErrorCode.PARAMS_ERROR.getMessage());
        }
        Boolean success = chartService.editChart(chartEditDTO);
        if(!success){
            log.error("修改我的图表信息失败");
            return Result.error(ErrorCode.OPERATION_ERROR.getMessage());
        }
        return Result.success("修改成功");
    }
}
