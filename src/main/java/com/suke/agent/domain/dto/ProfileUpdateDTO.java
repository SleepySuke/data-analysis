/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 用户画像更新请求DTO
 */

package com.suke.agent.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ProfileUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Size(max = 64, message = "行业不能超过64字符")
    private String industry;

    @Size(max = 255, message = "偏好图表不能超过255字符")
    @Pattern(regexp = "^[a-zA-Z,]*$", message = "偏好图表格式不合法")
    private String preferredCharts;

    @Size(max = 16, message = "详情级别不能超过16字符")
    @Pattern(regexp = "^(brief|standard|detailed)?$", message = "详情级别应为 brief/standard/detailed")
    private String detailLevel;

    @Size(max = 32, message = "报告风格不能超过32字符")
    @Pattern(regexp = "^(academic|business|casual)?$", message = "报告风格应为 academic/business/casual")
    private String reportStyle;
}
