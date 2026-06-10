/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 用户画像VO
 */

package com.suke.agent.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProfileVO {
    private Long userId;
    private String industry;
    private String preferredCharts;
    private String detailLevel;
    private String reportStyle;
}
