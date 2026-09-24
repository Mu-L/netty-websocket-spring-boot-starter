package org.yeauty.lifecycle;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
 * 关闭事件延迟转发的端点：{@code @OnOpen} 中在 pipeline 最前面插入 {@code delayed-inactive}，
 * 使网络线程上的 {@code channelInactive} 转发明显晚于线程池的静默期。
 * <p>
 * 该延迟只模拟"网络线程的关闭事件转发晚于业务执行器的静默期"这一竞态，不修改生产代码。
 * 若停机只按次序发出异步请求而不等待线程池<em>真正终止</em>，就会得到
 * {@code OPENED=1 CLOSED=0 TERMINATED=true}。
 */
@ServerEndpoint(path = "/lifecycle-delayed", port = "${test.delayed.port}")
public class DelayedCloseEndpoint {

    /**
     * 大于线程池静默期（{@code 100ms}）的转发延迟。
     */
    public static final long CLOSE_FORWARD_DELAY_MILLIS = 600L;

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
        session.pipeline().addFirst("delayed-inactive", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                Thread.sleep(CLOSE_FORWARD_DELAY_MILLIS);
                super.channelInactive(ctx);
            }
        });
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        session.sendText("lifecycle-delayed:" + message);
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
