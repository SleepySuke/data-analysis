/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent追踪MyBatis映射器
 */

package com.suke.agent.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suke.agent.trace.AgentTrace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTrace> {
}
