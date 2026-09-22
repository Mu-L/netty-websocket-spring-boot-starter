package org.yeauty.endpoint;

import org.yeauty.annotation.OnClose;
import org.yeauty.annotation.OnError;
import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.OnOpen;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 回声端点：{@code port = "0"} 让框架自动分配一个空闲端口。
 */
@ServerEndpoint(path = "/echo", port = "0")
public class EchoEndpoint {

    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();

    public static int sessionCount() {
        return SESSIONS.size();
    }

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        session.sendText("echo:" + message);
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        SESSIONS.remove(session);
    }
}
