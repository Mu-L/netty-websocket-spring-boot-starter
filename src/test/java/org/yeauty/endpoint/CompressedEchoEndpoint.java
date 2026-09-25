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
 * 压缩端点：{@code useCompressionHandler = "true"}。
 * <p>
 * 压缩是 Netty 服务级别的配置（同一 host + port 上的端点共享一个服务），
 * 因此这里额外绑定到 127.0.0.1，与默认 host 上的端点互不干扰。
 */
@ServerEndpoint(path = "/echo-compress", host = "127.0.0.1", port = "0", useCompressionHandler = "true", maxMessagePayloadLength = "256")
public class CompressedEchoEndpoint {

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
        session.sendText("compressed:" + message);
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
    }

    public static volatile Throwable lastError;

    @OnError
    public void onError(Session session, Throwable throwable) {
        lastError = throwable;
        SESSIONS.remove(session);
    }
}
