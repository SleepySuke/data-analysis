package com.suke.common;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author 自然醒
 * @version 1.0
 */
//用于封装websocket消息
@Data
@Accessors(chain = true)
public class WebSocketMessage {
    /**
     * 消息类型
     */
    private String type;
    /**
     * 状态
     */
    private String status;
    /**
     * 消息内容
     */
    private String message;
    /**
     * 数据
     */
    private Object data;

}
