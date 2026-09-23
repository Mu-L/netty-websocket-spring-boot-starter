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
 * 生命周期端点：端口由测试通过 {@code ${test.port}} 显式注入，
 * 因此不会读写 {@code ServerEndpointConfig} 的随机端口缓存，避免污染其他测试。
 */
@ServerEndpoint(path = "/lifecycle", port = "${test.port}")
public class LifecycleEndpoint {

    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();
    private static final AtomicInteger OPENED = new AtomicInteger();
    private static final AtomicInteger CLOSED = new AtomicInteger();

    public static int sessionCount() {
        return SESSIONS.size();
    }

    public static int openCount() {
        return OPENED.get();
    }

    public static int closeCount() {
        return CLOSED.get();
    }

    public static void reset() {
        SESSIONS.clear();
        OPENED.set(0);
        CLOSED.set(0);
    }

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        OPENED.incrementAndGet();
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        session.sendText("lifecycle:" + message);
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
        CLOSED.incrementAndGet();
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        SESSIONS.remove(session);
    }
}
