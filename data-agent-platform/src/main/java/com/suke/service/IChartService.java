package com.suke.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.suke.domain.dto.chart.ChartAddDTO;
import com.suke.domain.dto.chart.ChartEditDTO;
import com.suke.domain.dto.chart.ChartPageQueryDTO;
import com.suke.domain.dto.file.UploadFileDTO;
import com.suke.domain.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.suke.domain.vo.GenChartVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 图表信息表 服务类
 * </p>
 *
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
public interface IChartService extends IService<Chart> {
    Long addChart(ChartAddDTO chartAddDTO);

    Chart getChartById(Long Id);

    /**
     * 同步分析文件
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    GenChartVO analysisFile(MultipartFile multipartFile, UploadFileDTO fileDTO);

    /**
     * 异步分析文件
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    GenChartVO asyncAnalyzeFile(MultipartFile multipartFile, UploadFileDTO fileDTO);

    /**
     * 通过mq进行异步分析
     * @param multipartFile
     * @param fileDTO
     * @return
     */
    GenChartVO asyncAnalyze(MultipartFile multipartFile, UploadFileDTO fileDTO);

    Page<Chart> getMyChartList(ChartPageQueryDTO chartPageQueryDTO);

    Boolean editChart(ChartEditDTO chartEditDTO);
}
