/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 产物展示VO
 */

package com.suke.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactVO {
    private String id;
    private String type;
    private String title;
    private Map<String, Object> payload;
}
