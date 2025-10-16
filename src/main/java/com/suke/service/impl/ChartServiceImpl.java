package com.suke.service.impl;

import com.suke.domain.entity.Chart;
import com.suke.mapper.ChartMapper;
import com.suke.service.IChartService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart> implements IChartService {

}
