package org.yeauty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.yeauty.autoconfigure.NettyWebSocketAutoConfigure;
import org.yeauty.endpoint.AliasEndpoint;
import org.yeauty.endpoint.BinaryEchoEndpoint;
import org.yeauty.endpoint.BroadcastEndpoint;
import org.yeauty.endpoint.CompressedEchoEndpoint;
import org.yeauty.endpoint.EchoEndpoint;
import org.yeauty.endpoint.PathEndpoint;
import org.yeauty.endpoint.TestEndpointConfig;
import org.yeauty.standard.ServerEndpointConfig;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 端到端集成测试：在真实的 Spring 容器 + Netty 服务上验证 WebSocket 收发、
 * 广播、路径参数、压缩协商以及优雅关停。
 */
class NettyWebSocketIntegrationTest {

    private static final int MAX_FRAME_LENGTH = 65536;
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String ANY_HOST = "0.0.0.0";

    private static AnnotationConfigApplicationContext context;
    private static int port;
    private static int compressPort;

    @BeforeAll
    static void startServer() {
        context = new AnnotationConfigApplicationContext(TestEndpointConfig.class);
        Integer anyHostPort = ServerEndpointConfig.getRandomPort(ANY_HOST);
        Integer localHostPort = ServerEndpointConfig.getRandomPort(LOCAL_HOST);
        assertNotNull(anyHostPort, "0.0.0.0 上的随机端口未分配");
        assertNotNull(localHostPort, "127.0.0.1 上的随机端口未分配");
        port = anyHostPort;
        compressPort = localHostPort;
        awaitPortOpen(port);
        awaitPortOpen(compressPort);
    }

    @AfterAll
    static void stopServer() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("文本消息回声")
    void echoTextMessage() throws Exception {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect("/echo", listener);
        try {
            webSocket.sendText("hello", true).join();
            assertEquals("echo:hello", listener.messages.poll(10, TimeUnit.SECONDS));
        } finally {
            closeQuietly(webSocket);
        }
    }

    @Test
    @DisplayName("消息广播到所有会话")
    void broadcastToAllSessions() throws Exception {
        CollectingListener first = new CollectingListener();
        CollectingListener second = new CollectingListener();
        WebSocket firstSocket = connect("/broadcast", first);
        WebSocket secondSocket = connect("/broadcast", second);
        try {
            awaitUntil(() -> BroadcastEndpoint.sessionCount() == 2, "两个会话未全部建立");

            firstSocket.sendText("hi", true).join();

            assertEquals("broadcast:hi", first.messages.poll(10, TimeUnit.SECONDS));
            assertEquals("broadcast:hi", second.messages.poll(10, TimeUnit.SECONDS));
        } finally {
            closeQuietly(firstSocket);
            closeQuietly(secondSocket);
        }
        awaitUntil(() -> BroadcastEndpoint.sessionCount() == 0, "会话未在关闭后清理");
    }

    @Test
    @DisplayName("路径变量解析")
    void pathVariableIsResolved() throws Exception {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect("/user/42", listener);
        try {
            awaitUntil(() -> PathEndpoint.openedIds().contains("42"), "onOpen 未解析到路径变量");

            webSocket.sendText("ping", true).join();

            assertEquals("user:42:ping", listener.messages.poll(10, TimeUnit.SECONDS));
        } finally {
            closeQuietly(webSocket);
        }
    }

    @Test
    @DisplayName("路径变量支持 value 简写，查询参数缺失时回退默认值")
    void pathVariableValueAliasIsResolved() {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect("/alias/7?token=abc", listener);
        try {
            awaitUntil(() -> AliasEndpoint.opened().contains("7|abc|raw"),
                    "value 简写未绑定路径变量或查询参数，实际记录=" + AliasEndpoint.opened());
        } finally {
            closeQuietly(webSocket);
        }
    }

    @Test
    @DisplayName("查询参数支持 name 形式并覆盖默认值")
    void requestParamNamedAliasIsResolved() {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect("/alias/8?token=t8&mode=fast", listener);
        try {
            awaitUntil(() -> AliasEndpoint.opened().contains("8|t8|fast"),
                    "name 形式的查询参数未正确绑定，实际记录=" + AliasEndpoint.opened());
        } finally {
            closeQuietly(webSocket);
        }
    }

    @Test
    @DisplayName("value 简写的路径变量在消息阶段同样生效")
    void pathVariableAliasIsResolvedOnMessage() throws Exception {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect("/alias/9?token=abc", listener);
        try {
            webSocket.sendText("ping", true).join();
            assertEquals("alias:9:ping", listener.messages.poll(10, TimeUnit.SECONDS));
        } finally {
            closeQuietly(webSocket);
        }
    }

    @Test
    @DisplayName("二进制消息回声")
    void echoBinaryMessage() throws Exception {
        BinaryListener listener = new BinaryListener();
        WebSocket webSocket = connect("/echo-binary", listener);
        try {
            awaitUntil(() -> BinaryEchoEndpoint.sessionCount() == 1, "二进制会话未建立");

            webSocket.sendBinary(StandardCharsets.UTF_8.encode("bytes"), true).join();

            ByteBuffer reply = listener.messages.poll(10, TimeUnit.SECONDS);
            assertNotNull(reply, "未收到二进制响应");
            assertEquals("bytes!", StandardCharsets.UTF_8.decode(reply).toString());
        } finally {
            closeQuietly(webSocket);
        }
    }

    @Test
    @DisplayName("开启压缩处理器后完成 permessage-deflate 协商")
    void compressionIsNegotiatedWithNettyClient() throws Exception {
        assertCompressionExchange("hello", false);
    }

    @Test
    void oversizedDecompressedMessageDisconnectsWithoutDelivery() throws Exception {
        assertCompressionExchange("x".repeat(262144), true);
    }

    @Test
    void compressedMessageAtExactLimitIsDelivered() throws Exception {
        assertCompressionExchange("x".repeat(256), false);
    }

    @Test
    void decodedMessageOverLimitClosesEvenBelowAllocationBound() throws Exception {
        assertCompressionExchange("x".repeat(257), true);
    }

    private void assertCompressionExchange(String payload, boolean oversized) throws Exception {
        CompressedEchoEndpoint.lastError = null;
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            AtomicReference<String> extensions = new AtomicReference<>();
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            CountDownLatch handshakeDone = new CountDownLatch(1);

            URI uri = new URI("ws://" + LOCAL_HOST + ":" + compressPort + "/echo-compress");
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), MAX_FRAME_LENGTH);

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpClientCodec());
                            pipeline.addLast(new HttpObjectAggregator(MAX_FRAME_LENGTH));
                            pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);
                            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    if (msg instanceof HttpResponse response) {
                                        String header = response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);
                                        if (header != null) {
                                            extensions.set(header);
                                        }
                                    }
                                    ctx.fireChannelRead(msg);
                                }
                            });
                            pipeline.addLast(new WebSocketClientProtocolHandler(handshaker));
                            pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                                        handshakeDone.countDown();
                                    }
                                    ctx.fireUserEventTriggered(evt);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof TextWebSocketFrame frame) {
                                        received.add(frame.text());
                                    } else {
                                        received.add("<" + msg.getClass().getSimpleName() + ">");
                                    }
                                }
                            });
                        }
                    });

            Channel channel = bootstrap.connect(LOCAL_HOST, compressPort).sync().channel();
            try {
                assertTrue(handshakeDone.await(10, TimeUnit.SECONDS), "websocket 握手未完成");
                awaitUntil(() -> CompressedEchoEndpoint.sessionCount() == 1, "服务端未建立会话");

                channel.writeAndFlush(new TextWebSocketFrame(payload)).sync();

                if (oversized) {
                    assertTrue(channel.closeFuture().await(10, TimeUnit.SECONDS), "Oversized decompressed message should close the connection");
                    assertTrue(received.isEmpty(), "Oversized message must not reach the endpoint");
                } else {
                    assertEquals("compressed:" + payload, received.poll(10, TimeUnit.SECONDS), "Server error: " + CompressedEchoEndpoint.lastError);
                }

                String negotiated = extensions.get();
                assertNotNull(negotiated, "握手响应中没有 Sec-WebSocket-Extensions 头");
                assertTrue(negotiated.toLowerCase(Locale.ROOT).contains("permessage-deflate"),
                        "未协商 permessage-deflate，实际为: " + negotiated);
            } finally {
                channel.close().await(5, TimeUnit.SECONDS);
            }
        } finally {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    @DisplayName("客户端关闭后触发 onClose")
    void sessionIsRemovedAfterClose() throws Exception {
        CollectingListener listener = new CollectingListener();
        WebSocket webSocket = connect("/echo", listener);
        try {
            awaitUntil(() -> EchoEndpoint.sessionCount() == 1, "会话未建立");
        } finally {
            closeQuietly(webSocket);
        }
        awaitUntil(() -> EchoEndpoint.sessionCount() == 0, "会话未在关闭后清理");
    }

    @Test
    @DisplayName("随机端口按 host 分别缓存")
    void randomPortIsAllocatedPerHost() {
        assertEquals(port, ServerEndpointConfig.getRandomPort(ANY_HOST));
        assertEquals(compressPort, ServerEndpointConfig.getRandomPort(LOCAL_HOST));
        assertTrue(port != compressPort, "不同 host 的随机端口不应相同");
    }

    @Test
    @DisplayName("自动装配入口注册到 AutoConfiguration.imports")
    void autoConfigurationIsExported() throws Exception {
        List<String> imported = new ArrayList<>();
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        while (resources.hasMoreElements()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resources.nextElement().openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        imported.add(line);
                    }
                }
            }
        }
        assertTrue(imported.contains(NettyWebSocketAutoConfigure.class.getName()),
                "AutoConfiguration.imports 中缺少 " + NettyWebSocketAutoConfigure.class.getName());
        assertNotNull(NettyWebSocketAutoConfigure.class.getAnnotation(AutoConfiguration.class),
                "自动配置类缺少 @AutoConfiguration");
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

    private static WebSocket connect(String path, WebSocket.Listener listener) {
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://" + LOCAL_HOST + ":" + port + path), listener)
                .join();
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
    }

    /**
     * 收集服务端推送的二进制消息。
     */
    private static final class BinaryListener implements WebSocket.Listener {

        private final BlockingQueue<ByteBuffer> messages = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            buffer.write(chunk, 0, chunk.length);
            if (last) {
                messages.add(ByteBuffer.wrap(buffer.toByteArray()));
                buffer.reset();
            }
            webSocket.request(1);
            return null;
        }
    }
}
