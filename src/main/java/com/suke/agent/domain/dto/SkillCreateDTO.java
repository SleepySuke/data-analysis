/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill创建请求DTO
 */

package com.suke.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class SkillCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Skill名称不能为空")
    @Size(min = 3, max = 64, message = "Skill名称长度需在3-64字符之间")
    private String skillName;

    @NotBlank(message = "描述不能为空")
    @Size(max = 256, message = "描述不能超过256字符")
    private String description;

    @NotBlank(message = "指令模板不能为空")
    @Size(max = 10000, message = "指令模板不能超过10000字符")
    private String promptTemplate;

    private String allowedTools;
    private String agentName;

    @Size(max = 5000, message = "扩展信息不能超过5000字符")
    private String extension;
}
