/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill参数模型，定义Skill的输入参数结构
 */

package com.suke.agent.skill.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillParameter {
    private String name;
    private String type;
    private String description;
    private boolean required;
    private String defaultValue;
}
