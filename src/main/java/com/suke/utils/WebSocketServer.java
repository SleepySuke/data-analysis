package com.suke.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suke.common.WebSocketMessage;
import com.suke.json.JacksonObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 自然醒
 * @version 1.0
 */
//websocket服务
@Component
@Slf4j
@ServerEndpoint("/websocket/{id}")
public class WebSocketServer {
    //存储在线用户的集合
    private static final ConcurrentHashMap <String, WebSocketServer> webSocketMap = new ConcurrentHashMap<>();
    //与客户端连接的会话
    private Session session;
    //用户id
    private String id;

    private static final ObjectMapper objectMapper = new JacksonObjectMapper();

    //连接建立的调用
    @OnOpen
    public void onOpen(Session session, @PathParam("id") String id){
        log.info("连接来自:{}", id);
        this.session = session;
        this.id = id;

        // 调试：打印当前所有连接
        log.info("当前连接数: {}", webSocketMap.size());
        log.info("当前连接用户: {}", webSocketMap.keySet());

        // 原子操作：同一用户重复连接时关闭旧连接并替换
        webSocketMap.compute(id, (key, oldServer) -> {
            if (oldServer != null && oldServer.session != null && oldServer.session.isOpen()) {
                try {
                    oldServer.session.close();
                } catch (Exception e) {
                    log.error("关闭旧连接失败", e);
                }
            }
            return this;
        });
        log.info("用户 {} 的连接已添加到Map，当前Map大小: {}", id, webSocketMap.size());

        // 发送 JSON 格式的连接成功消息
        try {
            WebSocketMessage wsMsg = new WebSocketMessage();
            wsMsg.setType("connect")
                    .setMessage("连接成功")
                    .setStatus("connected");
            String jsonMessage = objectMapper.writeValueAsString(wsMsg);
            this.session.getAsyncRemote().sendText(jsonMessage);
            log.info("已向用户 {} 发送连接成功消息: {}", id, jsonMessage);
        } catch (Exception e) {
            log.error("发送连接成功消息失败", e);
        }
    }

    /**
     * 接收到客户端消息调用
     * @param message
     */
    @OnMessage
    public void onMessage(String message){
        log.info("来自客户端 {} 的消息:{}", id, message);

        // 处理心跳消息
        if ("ping".equals(message)) {
            try {
                WebSocketMessage wsMsg = new WebSocketMessage();
                wsMsg.setType("pong")
                        .setMessage("pong")
                        .setStatus("success");
                String jsonMessage = objectMapper.writeValueAsString(wsMsg);
                this.session.getAsyncRemote().sendText(jsonMessage);
                log.info("向用户 {} 发送pong响应", id);
            } catch (Exception e) {
                log.error("发送pong消息失败", e);
            }
        } else {
            // 其他消息处理
            log.info("收到客户端 {} 的其他消息: {}", id, message);
            // 可以在这里处理其他类型的消息
        }
    }

    /**
     * 连接关闭调用
     */
    @OnClose
    public void onClose(){
        log.info("用户:{}已退出",id);
        webSocketMap.remove(id);
    }

    /**
     * 发生错误调用
     * @param error
     */
    @OnError
    public void onError(Throwable error){
        log.error("发生错误:{}",error);
        webSocketMap.remove(id);
    }

    public void sendMessage(String message){
        this.session.getAsyncRemote().sendText(message);
    }

    public void sendMessageToUser(String userId, WebSocketMessage wsMsg){
        log.info("发送给用户:{}的消息:{}",userId,wsMsg);
        if(webSocketMap.containsKey(userId)){
            Session session = webSocketMap.get(userId).session;
            if(session != null && session.isOpen()){
                try{
                    String message = objectMapper.writeValueAsString(wsMsg);
                    log.info("发送给用户:{}的消息:{}",userId,message);
                    session.getBasicRemote().sendText(message);
                } catch (Exception e) {
                    log.error("发送消息异常:{}",e);
                }
            }else{
                log.error("用户:{}已退出",userId);
                webSocketMap.remove(userId);
            }
        }else{
            log.error("用户:{}不存在",userId);
        }
    }

}
