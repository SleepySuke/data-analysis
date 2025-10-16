package com.suke.mapper;

import com.suke.domain.entity.Chart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 图表信息表 Mapper 接口
 * </p>
 *
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
@Mapper
public interface ChartMapper extends BaseMapper<Chart> {

}
