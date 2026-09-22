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
 * 广播端点：收到的消息会推送给当前所有在线会话。
 */
@ServerEndpoint(path = "/broadcast", port = "0")
public class BroadcastEndpoint {

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
        for (Session online : SESSIONS) {
            online.sendText("broadcast:" + message);
        }
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
