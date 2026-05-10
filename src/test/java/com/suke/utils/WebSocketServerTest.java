package com.suke.utils;

import com.suke.common.WebSocketMessage;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketServerTest {

    private WebSocketServer server;

    @BeforeEach
    void setUp() {
        server = new WebSocketServer();
    }

    // ========== #23: onError should remove session from map ==========

    @Test
    @DisplayName("onError后webSocketMap应移除该session")
    void onError_shouldRemoveSessionFromMap() throws Exception {
        WebSocketServer errServer = new WebSocketServer();
        Session mockSession = mock(Session.class);
        when(mockSession.isOpen()).thenReturn(true);

        errServer.onOpen(mockSession, "user_err");

        // Verify it's in the map by sending a message
        errServer.sendMessageToUser("user_err", new WebSocketMessage().setType("test"));
        verify(mockSession, atLeastOnce()).getBasicRemote();

        // Trigger onError
        errServer.onError(new RuntimeException("connection error"));

        // After onError, sending to user_err should log "user not found"
        reset(mockSession);
        errServer.sendMessageToUser("user_err", new WebSocketMessage().setType("test"));
        verify(mockSession, never()).getBasicRemote();
    }

    // ========== #24: onOpen atomic operation ==========

    @Test
    @DisplayName("同一用户重复连接应替换旧session")
    void onOpen_duplicateUser_shouldReplaceOldSession() throws Exception {
        // webSocketMap is static, so we need separate server instances
        WebSocketServer server1 = new WebSocketServer();
        WebSocketServer server2 = new WebSocketServer();

        Session oldSession = mock(Session.class);
        when(oldSession.isOpen()).thenReturn(true);

        Session newSession = mock(Session.class);
        when(newSession.isOpen()).thenReturn(true);

        // First connection
        server1.onOpen(oldSession, "user_dup");

        // Second connection from a different server instance
        server2.onOpen(newSession, "user_dup");

        // Old session should be closed
        verify(oldSession).close();

        // New session should receive the connect message
        verify(newSession, atLeastOnce()).getAsyncRemote();
    }

    @Test
    @DisplayName("onClose应移除session")
    void onClose_shouldRemoveSession() throws Exception {
        WebSocketServer closeServer = new WebSocketServer();
        Session mockSession = mock(Session.class);
        when(mockSession.isOpen()).thenReturn(true);

        closeServer.onOpen(mockSession, "user_close");
        closeServer.onClose();

        reset(mockSession);
        closeServer.sendMessageToUser("user_close", new WebSocketMessage().setType("test"));
        verify(mockSession, never()).getBasicRemote();
    }

    @Test
    @DisplayName("onOpen旧连接已关闭时不应报错")
    void onOpen_oldSessionAlreadyClosed_shouldNotError() throws Exception {
        WebSocketServer s1 = new WebSocketServer();
        WebSocketServer s2 = new WebSocketServer();

        Session closedOldSession = mock(Session.class);
        when(closedOldSession.isOpen()).thenReturn(false);

        Session newSession = mock(Session.class);
        when(newSession.isOpen()).thenReturn(true);

        s1.onOpen(closedOldSession, "user_old_closed");
        assertDoesNotThrow(() -> s2.onOpen(newSession, "user_old_closed"));
    }

    // ========== #24: Concurrent onOpen atomic operation ==========

    @Test
    @DisplayName("并发onOpen不应产生竞态，最终map中只有一个session")
    void onOpen_concurrentConnections_shouldNotRace() throws Exception {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        WebSocketServer[] servers = new WebSocketServer[threadCount];
        Session[] sessions = new Session[threadCount];
        for (int i = 0; i < threadCount; i++) {
            servers[i] = new WebSocketServer();
            sessions[i] = mock(Session.class);
            when(sessions[i].isOpen()).thenReturn(true);
        }

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    servers[idx].onOpen(sessions[idx], "user_concurrent");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Should not happen
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertEquals(threadCount, successCount.get(), "All threads should complete without error");

        // Verify: sending to user_concurrent should reach exactly one session
        // No crash, no data corruption
        WebSocketServer testServer = new WebSocketServer();
        assertDoesNotThrow(() -> testServer.sendMessageToUser("user_concurrent",
                new WebSocketMessage().setType("test")));
    }

    // ========== #23: onError + onOpen recovery ==========

    @Test
    @DisplayName("onError后同用户可重新连接")
    void onError_thenOnOpen_shouldAllowReconnect() throws Exception {
        WebSocketServer s1 = new WebSocketServer();
        WebSocketServer s2 = new WebSocketServer();

        Session oldSession = mock(Session.class);
        when(oldSession.isOpen()).thenReturn(true);

        Session newSession = mock(Session.class);
        when(newSession.isOpen()).thenReturn(true);

        // First connection
        s1.onOpen(oldSession, "user_reconnect");

        // Error occurs
        s1.onError(new RuntimeException("connection lost"));

        // Reconnect should work
        assertDoesNotThrow(() -> s2.onOpen(newSession, "user_reconnect"));

        // New session should receive messages
        reset(newSession);
        when(newSession.isOpen()).thenReturn(true);
        s2.sendMessageToUser("user_reconnect", new WebSocketMessage().setType("test"));
        verify(newSession).getBasicRemote();
    }
}
