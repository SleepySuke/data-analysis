/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill展示VO
 */

package com.suke.agent.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SkillVO {
    private Long id;
    private String skillName;
    private String description;
    private String agentName;
    private String ownerType;
    private Integer usageCount;
    private Boolean isPublic;
    private String extension;
}
