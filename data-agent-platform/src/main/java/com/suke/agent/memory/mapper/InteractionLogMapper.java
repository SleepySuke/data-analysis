/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 交互日志MyBatis映射器
 */

package com.suke.agent.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suke.agent.memory.model.InteractionLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InteractionLogMapper extends BaseMapper<InteractionLog> {

    @Select("SELECT DISTINCT topic FROM agent_interaction_log " +
            "WHERE user_id = #{userId} AND topic IS NOT NULL AND create_time > DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY topic ORDER BY COUNT(*) DESC LIMIT #{limit}")
    List<String> selectRecentTopics(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM agent_interaction_log WHERE create_time < DATE_SUB(NOW(), INTERVAL 90 DAY)")
    int purgeOldLogs();
}
