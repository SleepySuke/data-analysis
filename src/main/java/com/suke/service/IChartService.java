package com.suke.service;

import com.suke.domain.dto.chart.ChartAddDTO;
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

    GenChartVO analysisFile(MultipartFile multipartFile, UploadFileDTO fileDTO);
}
