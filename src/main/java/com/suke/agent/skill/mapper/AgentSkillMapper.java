/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent Skill MyBatis映射器
 */

package com.suke.agent.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suke.agent.skill.model.SkillDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentSkillMapper extends BaseMapper<SkillDefinition> {
}
