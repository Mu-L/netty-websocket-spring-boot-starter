package org.yeauty.lifecycle;

import org.yeauty.annotation.BeforeHandshake;
import org.yeauty.annotation.OnClose;
import org.yeauty.annotation.OnError;
import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.OnOpen;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 握手可暂停的端点：{@code @BeforeHandshake} 会一直阻塞直到测试放行。
 * <p>
 * 用于覆盖"握手尚未完成时关闭容器"这一路：这类连接还没有完成握手，
 * 不在 {@link org.yeauty.standard.WebsocketServer} 的 {@code ChannelGroup} 中，
 * 只能靠 worker 的退出屏障兜底 —— 即业务执行器必须在 worker 真正终止之后才停止，
 * 否则放行后的 {@code pipeline.addLast(eventExecutorGroup, ...)} 会打向已终止的执行器。
 */
@ServerEndpoint(path = "/lifecycle-handshake", port = "${test.handshake.port}")
public class HandshakeGateEndpoint {

    private static final long MAX_HANDSHAKE_WAIT_SECONDS = 30L;

    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();
    private static final AtomicInteger OPENED = new AtomicInteger();
    private static final AtomicInteger CLOSED = new AtomicInteger();

    private static volatile CountDownLatch handshakeStarted = new CountDownLatch(1);
    private static volatile CountDownLatch handshakeRelease = new CountDownLatch(1);

    public static int sessionCount() {
        return SESSIONS.size();
    }

    public static int openCount() {
        return OPENED.get();
    }

    public static int closeCount() {
        return CLOSED.get();
    }

    /**
     * @return 握手进入 {@code @BeforeHandshake} 时被打开的闸门，测试据此确认握手已暂停
     */
    public static CountDownLatch handshakeStartedLatch() {
        return handshakeStarted;
    }

    /**
     * 放行被暂停的握手。
     */
    public static void releaseHandshake() {
        handshakeRelease.countDown();
    }

    public static void reset() {
        SESSIONS.clear();
        OPENED.set(0);
        CLOSED.set(0);
        handshakeStarted = new CountDownLatch(1);
        handshakeRelease = new CountDownLatch(1);
    }

    @BeforeHandshake
    public void handshake(Session session) {
        handshakeStarted.countDown();
        try {
            handshakeRelease.await(MAX_HANDSHAKE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        OPENED.incrementAndGet();
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        session.sendText("lifecycle-handshake:" + message);
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
