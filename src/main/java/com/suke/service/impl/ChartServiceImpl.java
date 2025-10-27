package com.suke.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.suke.context.UserContext;
import com.suke.domain.dto.chart.ChartAddDTO;
import com.suke.domain.entity.Chart;
import com.suke.mapper.ChartMapper;
import com.suke.service.IChartService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suke.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
