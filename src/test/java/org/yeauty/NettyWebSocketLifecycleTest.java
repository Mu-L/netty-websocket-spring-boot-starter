package org.yeauty;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.yeauty.lifecycle.LifecycleEndpoint;
import org.yeauty.lifecycle.LifecycleEndpointConfig;
import org.yeauty.lifecycle.LifecycleLocalEndpoint;
import org.yeauty.standard.ServerEndpointExporter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 生命周期测试：端口由用例显式指定（不碰随机端口缓存），每个用例拥有独立的容器与端口，
 * 因此执行顺序不影响结果。
 */
class NettyWebSocketLifecycleTest {

    private static final String LOCAL_HOST = "127.0.0.1";

    private AnnotationConfigApplicationContext context;
    private ServerEndpointExporter exporter;
    private int port;
    private int localPort;

    @BeforeEach
    void startServer() {
        LifecycleEndpoint.reset();
        LifecycleLocalEndpoint.reset();
        port = reserveFreePort();
        localPort = reserveFreePort();
        context = newContext(port, localPort);
        exporter = context.getBean(ServerEndpointExporter.class);
        awaitPortOpen(port);
        awaitPortOpen(localPort);
    }

    @AfterEach
    void stopServer() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    @DisplayName("容器关闭后释放端口")
    void contextCloseReleasesPort() {
        context.close();

        awaitPortFree(port);
        awaitPortFree(localPort);
    }

    @Test
    @DisplayName("保持活跃连接关闭容器时执行 onClose")
    void activeSessionsReceiveOnCloseOnContainerShutdown() {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect(port, "/lifecycle", listener);
        try {
            awaitUntil(() -> LifecycleEndpoint.sessionCount() == 1, "会话未建立");
            assertEquals(1, LifecycleEndpoint.openCount(), "onOpen 执行次数不符合预期");

            // 连接保持存活的情况下关闭容器
            context.close();

            assertTrue(exporter.awaitTermination(5, TimeUnit.SECONDS), "容器关闭后线程池未终止");
            awaitUntil(() -> LifecycleEndpoint.sessionCount() == 0, "容器关闭时 @OnClose 未执行，会话未被清理");
            assertEquals(1, LifecycleEndpoint.closeCount(), "@OnClose 执行次数不符合预期");
            awaitPortFree(port);
            awaitPortFree(localPort);
        } finally {
            closeQuietly(webSocket);
        }
    }

    @Test
    @DisplayName("配置中的每个服务都成功绑定并可通信")
    void allConfiguredServersBindSuccessfully() throws Exception {
        assertEcho(port, "/lifecycle", "lifecycle:ping");
        assertEcho(localPort, "/lifecycle-local", "lifecycle-local:ping");
    }

    @Test
    @DisplayName("端口已被占用时容器启动失败，而不是带着未监听的服务继续启动")
    void portConflictFailsStartup() {
        // 与当前运行中的容器使用同一组端口
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> newContext(port, localPort));
        assertTrue(failure.getMessage().contains("Failed to start websocket server"),
                "端口冲突应让容器启动失败，实际异常：" + failure.getMessage());
    }

    private void assertEcho(int targetPort, String path, String expected) {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect(targetPort, path, listener);
        try {
            webSocket.sendText("ping", true).join();
            assertEquals(expected, listener.messages.poll(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            closeQuietly(webSocket);
        }
    }

    /**
     * 通过属性显式指定端口：既不读取也不写入 {@code ServerEndpointConfig} 的随机端口缓存。
     */
    private static AnnotationConfigApplicationContext newContext(int port, int localPort) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> ports = new HashMap<>();
        ports.put("test.port", String.valueOf(port));
        ports.put("test.local.port", String.valueOf(localPort));
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test-websocket-ports", ports));
        context.register(LifecycleEndpointConfig.class);
        context.refresh();
        return context;
    }

    private static int reserveFreePort() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(0));
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("无法为测试预留空闲端口", e);
        }
    }

    private static WebSocket connect(int targetPort, String path, WebSocket.Listener listener) {
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://" + LOCAL_HOST + ":" + targetPort + path), listener)
                .join();
    }

    private static void closeQuietly(WebSocket webSocket) {
        try {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test finished").get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 关闭帧发送失败不影响测试结论
        } finally {
            webSocket.abort();
        }
    }

    private static void awaitPortOpen(int portToCheck) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(LOCAL_HOST, portToCheck), 300);
                return;
            } catch (IOException e) {
                sleepQuietly(50);
            }
        }
        fail("端口 " + portToCheck + " 在 10 秒内未开始监听");
    }

    private static void awaitPortFree(int portToCheck) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(LOCAL_HOST, portToCheck));
                return;
            } catch (IOException e) {
                sleepQuietly(50);
            }
        }
        fail("端口 " + portToCheck + " 在容器关闭后仍被占用");
    }

    private static void awaitUntil(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleepQuietly(50);
        }
        fail(message);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * 收集服务端推送的文本消息。
     */
    private static final class CollectingListener implements WebSocket.Listener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            assertNotNull(data, "不应收到二进制消息");
            return null;
        }
    }
}
