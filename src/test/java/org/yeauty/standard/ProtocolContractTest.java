package org.yeauty.standard;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.yeauty.annotation.*;
import org.yeauty.pojo.PojoEndpointServer;
import org.yeauty.pojo.PojoMethodMapping;
import org.yeauty.pojo.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolContractTest {
    private final ServerEndpointConfig config = new ServerEndpointConfig("0.0.0.0", 80, 1, 1, false,
            30000, 128, 16, 65536, 32768, -1, -1, true, false, -1, false, 0, 0, 0,
            65536, false, 1, "", "", "", "", "", "", "", new String[0], null, 16);

    @Test void missingBeforeHandshakeArgumentReturns400() throws Exception {
        assertEquals(400, handshake(RequiredBefore.class, "/", null).status);
    }
    @Test void missingOnOpenArgumentReturns400BeforeUpgrade() throws Exception {
        assertEquals(400, handshake(RequiredOpen.class, "/", null).status);
    }
    @Test void invalidOnOpenArgumentReturns400BeforeUpgrade() throws Exception {
        assertEquals(400, handshake(RequiredNumber.class, "/?q=not-a-number", null).status);
    }
    @Test void callbackExceptionAbortsHandshake() throws Exception {
        assertEquals(500, handshake(ThrowingHandshake.class, "/", null).status);
    }
    @Test void emptyQueryUsesDefaultInCallback() throws Exception {
        DefaultOpen.value = null;
        assertEquals(101, handshake(DefaultOpen.class, "/?q=", null).status);
        assertEquals("fallback", DefaultOpen.value);
    }
    @Test void selectsExactlyOneSupportedSubprotocol() throws Exception {
        Response response = handshake(SubprotocolEndpoint.class, "/", "other, chat");
        assertEquals(101, response.status);
        assertEquals(List.of("chat"), response.protocols);
    }
    @Test void doesNotEchoUnconfiguredSubprotocol() throws Exception {
        assertTrue(handshake(DefaultOpen.class, "/", "other, chat").protocols.isEmpty());
    }
    @Test void annotatedMessageStringKeepsItsBinding() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            PojoEndpointServer server = server(MessageArguments.class, context);
            EmbeddedChannel channel = new EmbeddedChannel();
            channel.attr(PojoEndpointServer.URI_TEMPLATE).set(java.util.Map.of("id", "42"));
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/?q=value");
            TextWebSocketFrame frame = new TextWebSocketFrame("body");
            try {
                server.prepareOnOpen(channel, request, "/");
                server.doOnOpen(channel, request, "/");
                server.doOnMessage(channel, frame);
                assertEquals("42|value|body", MessageArguments.value);
            } finally { frame.release(); request.release(); channel.finishAndReleaseAll(); }
        }
    }
    @Test void fragmentedMessagesBelowLimitAreDelivered() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketMessageSizeHandler(16), new WebSocketFrameAggregator(16));
        try {
            assertFalse(channel.writeInbound(new TextWebSocketFrame(false, 0, "12345678")));
            assertTrue(channel.writeInbound(new ContinuationWebSocketFrame(true, 0, "abcdefgh")));
            TextWebSocketFrame result = channel.readInbound();
            assertEquals("12345678abcdefgh", result.text());
            result.release();
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void controlFramesAreNotSubjectToApplicationMessageLimit() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketMessageSizeHandler(1));
        PingWebSocketFrame ping = new PingWebSocketFrame(io.netty.buffer.Unpooled.wrappedBuffer(new byte[16]));
        try {
            assertTrue(channel.writeInbound(ping));
            PingWebSocketFrame result = channel.readInbound();
            assertEquals(16, result.content().readableBytes());
            result.release();
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void oversizedSingleMessageClosesWith1009() throws Exception { assertTooLarge(false); }
    @Test void oversizedFragmentedMessageClosesWith1009() throws Exception { assertTooLarge(true); }

    private void assertTooLarge(boolean fragmented) throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            PojoEndpointServer server = server(DefaultOpen.class, context);
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketMessageSizeHandler(16),
                    new WebSocketFrameAggregator(16), new WebSocketServerHandler(server));
            try {
                if (fragmented) {
                    channel.writeInbound(new TextWebSocketFrame(false, 0, "1234567890"));
                    channel.writeInbound(new ContinuationWebSocketFrame(true, 0, "abcdefghij"));
                } else { channel.writeInbound(new TextWebSocketFrame("12345678901234567")); }
                CloseWebSocketFrame close = channel.readOutbound();
                assertNotNull(close);
                assertEquals(1009, close.statusCode());
                close.release();
                assertFalse(channel.isActive());
            } finally { channel.finishAndReleaseAll(); }
        }
    }

    private Response handshake(Class<?> endpoint, String uri, String protocols) throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            AtomicReference<Response> response = new AtomicReference<>();
            EmbeddedChannel client = new EmbeddedChannel(new HttpResponseDecoder(), new HttpObjectAggregator(65536),
                    new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                            response.set(new Response(msg.status().code(), new ArrayList<>(msg.headers().getAll(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL))));
                        }
                    });
            EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), new HttpObjectAggregator(65536),
                    new HttpServerHandler(server(endpoint, context), config, null,
                            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE), false));
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
            request.headers().set(HttpHeaderNames.HOST, "localhost")
                    .set(HttpHeaderNames.UPGRADE, "websocket").set(HttpHeaderNames.CONNECTION, "Upgrade")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
            if (protocols != null) request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, protocols);
            try {
                channel.writeInbound(request);
                channel.runPendingTasks();
                Object outbound;
                while ((outbound = channel.readOutbound()) != null) client.writeInbound(outbound);
                assertNotNull(response.get());
                return response.get();
            } finally { channel.finishAndReleaseAll(); client.finishAndReleaseAll(); }
        }
    }
    private PojoEndpointServer server(Class<?> endpoint, AnnotationConfigApplicationContext context) throws Exception {
        return new PojoEndpointServer(new PojoMethodMapping(endpoint, context, context.getDefaultListableBeanFactory()), config, "/");
    }
    record Response(int status, List<String> protocols) {}
    public static class RequiredBefore { @BeforeHandshake public void before(@RequestParam("q") String q) {} }
    public static class RequiredOpen { @OnOpen public void open(@RequestParam("q") String q) {} }
    public static class RequiredNumber { @OnOpen public void open(@RequestParam("q") int q) {} }
    public static class ThrowingHandshake { @BeforeHandshake public void before() { throw new IllegalStateException("test denial"); } }
    public static class DefaultOpen {
        static String value;
        @OnOpen public void open(@RequestParam(value="q", defaultValue="fallback") String q) { value=q; }
    }
    public static class SubprotocolEndpoint { @BeforeHandshake public void before(Session session) { session.setSubprotocols("chat"); } }
    public static class MessageArguments {
        static String value;
        @OnMessage public void message(@PathVariable("id") String id, @RequestParam("q") String q, String body) { value=id+"|"+q+"|"+body; }
    }
}
