package org.yeauty.endpoint;

import org.yeauty.annotation.OnBinary;
import org.yeauty.annotation.OnClose;
import org.yeauty.annotation.OnError;
import org.yeauty.annotation.OnOpen;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 二进制回声端点：{@code @OnBinary} 收到字节后追加一个标记字节再原样回显，
 * 用于验证二进制帧的收发链路。
 */
@ServerEndpoint(path = "/echo-binary", port = "0")
public class BinaryEchoEndpoint {

    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();

    public static int sessionCount() {
        return SESSIONS.size();
    }

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
    }

    @OnBinary
    public void onBinary(Session session, byte[] bytes) {
        byte[] echoed = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, echoed, 0, bytes.length);
        echoed[bytes.length] = '!';
        session.sendBinary(echoed);
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
