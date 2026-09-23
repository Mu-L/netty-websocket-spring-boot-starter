package org.yeauty.lifecycle;

import org.yeauty.annotation.OnClose;
import org.yeauty.annotation.OnError;
import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.OnOpen;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 绑定到 127.0.0.1 的生命周期端点，用于核实配置中的每个服务都成功绑定。
 */
@ServerEndpoint(path = "/lifecycle-local", host = "127.0.0.1", port = "${test.local.port}")
public class LifecycleLocalEndpoint {

    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();
    private static final AtomicInteger OPENED = new AtomicInteger();

    public static int sessionCount() {
        return SESSIONS.size();
    }

    public static int openCount() {
        return OPENED.get();
    }

    public static void reset() {
        SESSIONS.clear();
        OPENED.set(0);
    }

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        OPENED.incrementAndGet();
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        session.sendText("lifecycle-local:" + message);
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
