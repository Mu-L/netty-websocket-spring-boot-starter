package org.yeauty.standard;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.springframework.util.StringUtils;
import org.yeauty.exception.DeploymentException;
import org.yeauty.pojo.PojoEndpointServer;
import org.yeauty.util.SslUtils;

import javax.net.ssl.SSLException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * @author Yeauty
 * @version 1.0
 */
public class WebsocketServer {

    /**
     * 关闭阶段的静默期：给正在收尾的任务留出时间，避免刚提交的任务被丢弃。
     */
    private static final long SHUTDOWN_QUIET_PERIOD_MILLIS = 100L;

    /**
     * 单个线程池等待终止的上限（毫秒）。
     */
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 5_000L;

    private final PojoEndpointServer pojoEndpointServer;

    private final ServerEndpointConfig config;

    /**
     * 已完成握手的连接。{@link DefaultChannelGroup} 会在连接关闭后自动移除成员，
     * 因此这里不需要手动维护进出。
     */
    private final ChannelGroup channels =
            new DefaultChannelGroup("netty-websocket-channels", GlobalEventExecutor.INSTANCE);

    private volatile Channel serverChannel;
    private volatile EventLoopGroup boss;
    private volatile EventLoopGroup worker;
    private volatile EventExecutorGroup eventExecutorGroup;
    private volatile boolean destroyed;
    private Thread shutdownHook;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebsocketServer.class);

    public WebsocketServer(PojoEndpointServer webSocketServerHandler, ServerEndpointConfig serverEndpointConfig) {
        this.pojoEndpointServer = webSocketServerHandler;
        this.config = serverEndpointConfig;

    }

    public void init() throws InterruptedException, SSLException, DeploymentException {
        final SslContext sslCtx;
        if (StringUtils.hasLength(config.getKeyStore())) {
            sslCtx = SslUtils.createSslContext(config.getKeyPassword(), config.getKeyStore(), config.getKeyStoreType(), config.getKeyStorePassword(), config.getTrustStore(), config.getTrustStoreType(), config.getTrustStorePassword());
        } else {
            sslCtx = null;
        }
        String[] corsOrigins = config.getCorsOrigins();
        Boolean corsAllowCredentials = config.getCorsAllowCredentials();
        final CorsConfig corsConfig = createCorsConfig(corsOrigins, corsAllowCredentials);

        if (config.isUseEventExecutorGroup()) {
            this.eventExecutorGroup = new DefaultEventExecutorGroup(config.getEventExecutorGroupThreads() == 0 ? 16 : config.getEventExecutorGroupThreads());
        }
        this.boss = new MultiThreadIoEventLoopGroup(config.getBossLoopGroupThreads(), NioIoHandler.newFactory());
        this.worker = new MultiThreadIoEventLoopGroup(config.getWorkerLoopGroupThreads(), NioIoHandler.newFactory());
        EventExecutorGroup eventExecutorGroup = this.eventExecutorGroup;
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventExecutorGroup finalEventExecutorGroup = eventExecutorGroup;
        bootstrap.group(this.boss, this.worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis())
                .option(ChannelOption.SO_BACKLOG, config.getSoBacklog())
                .childOption(ChannelOption.WRITE_SPIN_COUNT, config.getWriteSpinCount())
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(config.getWriteBufferLowWaterMark(), config.getWriteBufferHighWaterMark()))
                .childOption(ChannelOption.TCP_NODELAY, config.isTcpNodelay())
                .childOption(ChannelOption.SO_KEEPALIVE, config.isSoKeepalive())
                .childOption(ChannelOption.SO_LINGER, config.getSoLinger())
                .childOption(ChannelOption.ALLOW_HALF_CLOSURE, config.isAllowHalfClosure())
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        if (sslCtx != null) {
                            pipeline.addFirst(sslCtx.newHandler(ch.alloc()));
                        }
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        if (corsConfig != null) {
                            pipeline.addLast(new CorsHandler(corsConfig));
                        }
                        pipeline.addLast(new HttpServerHandler(pojoEndpointServer, config, finalEventExecutorGroup, channels, corsConfig != null));
                    }
                });

        if (config.getSoRcvbuf() != -1) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, config.getSoRcvbuf());
        }

        if (config.getSoSndbuf() != -1) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, config.getSoSndbuf());
        }

        ChannelFuture channelFuture;
        if ("0.0.0.0".equals(config.getHost())) {
            channelFuture = bootstrap.bind(config.getPort());
        } else {
            try {
                channelFuture = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(config.getHost()), config.getPort()));
            } catch (UnknownHostException e) {
                channelFuture = bootstrap.bind(config.getHost(), config.getPort());
                e.printStackTrace();
            }
        }

        channelFuture.await();
        if (!channelFuture.isSuccess()) {
            // 绑定失败必须暴露出来：否则容器会带着一个从未监听的服务继续启动
            destroy();
            throw new DeploymentException(
                    String.format("websocket [%s:%s] bind fail", config.getHost(), config.getPort()),
                    channelFuture.cause());
        }
        this.serverChannel = channelFuture.channel();

        this.shutdownHook = new Thread(this::destroy, "netty-websocket-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
    }

    /**
     * 优雅关闭：先停止接受新连接，再关闭活跃连接并等待 {@code @OnClose} 回调执行完成，
     * 最后依次停止网络线程池与业务执行器。可重复调用。
     * <p>
     * 顺序很关键：{@code useEventExecutorGroup=true} 时业务 handler 被 pin 到业务执行器上，
     * 若业务执行器先停，连接关闭产生的 {@code channelInactive} 会因执行器已终止而被拒绝
     * （{@code RejectedExecutionException: event executor terminated}），{@code @OnClose} 回调将被丢弃。
     */
    public synchronized void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;

        Thread hook = this.shutdownHook;
        if (hook != null) {
            this.shutdownHook = null;
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException | SecurityException ignored) {
                // JVM 正在退出或不允许操作 shutdown hook，忽略即可
            }
        }

        // 1) 停止接受新连接
        Channel channel = this.serverChannel;
        if (channel != null) {
            this.serverChannel = null;
            channel.close().awaitUninterruptibly();
        }

        // 2) 业务执行器仍在运行时关闭活跃连接，保证 @OnClose 能被正常投递
        if (!channels.isEmpty()) {
            channels.writeAndFlush(new CloseWebSocketFrame())
                    .awaitUninterruptibly(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            awaitChannelGroupEmpty();
        }
        channels.close().awaitUninterruptibly(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        // 3) 网络线程池
        shutdownGracefully(this.boss);
        shutdownGracefully(this.worker);

        // 4) 业务执行器最后停，避免丢弃尚未执行完的 @OnClose
        shutdownGracefully(this.eventExecutorGroup);

        // 5) 等待全部终止：destroy() 返回时 @OnClose 已执行完毕
        awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        logger.info(String.format("\033[34mNetty WebSocket stopped on port: %s .\033[0m", config.getPort()));
    }

    /**
     * 等待 boss、worker 与业务执行器全部终止。
     *
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @return 全部终止返回 {@code true}，超时返回 {@code false}
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        long remaining = unit.toNanos(timeout);
        while (true) {
            if (isTerminated(this.boss) && isTerminated(this.worker) && isTerminated(this.eventExecutorGroup)) {
                return true;
            }
            if (remaining <= 0) {
                return false;
            }
            long start = System.nanoTime();
            try {
                Thread.sleep(Math.max(1L, Math.min(20L, TimeUnit.NANOSECONDS.toMillis(remaining))));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remaining -= System.nanoTime() - start;
        }
    }

    /**
     * 已完成握手的连接集合，供 {@link HttpServerHandler} 在握手成功后登记连接。
     */
    public ChannelGroup getChannelGroup() {
        return channels;
    }

    private void awaitChannelGroupEmpty() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_TIMEOUT_MILLIS);
        while (!channels.isEmpty() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void shutdownGracefully(EventExecutorGroup group) {
        if (group != null) {
            group.shutdownGracefully(SHUTDOWN_QUIET_PERIOD_MILLIS, SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private static boolean isTerminated(EventExecutorGroup group) {
        return group == null || group.isTerminated();
    }

    private CorsConfig createCorsConfig(String[] corsOrigins, Boolean corsAllowCredentials) {
        if (corsOrigins.length == 0) {
            return null;
        }
        CorsConfigBuilder corsConfigBuilder = null;
        for (String corsOrigin : corsOrigins) {
            if ("*".equals(corsOrigin)) {
                corsConfigBuilder = CorsConfigBuilder.forAnyOrigin();
                break;
            }
        }
        if (corsConfigBuilder == null) {
            corsConfigBuilder = CorsConfigBuilder.forOrigins(corsOrigins);
        }
        if (corsAllowCredentials != null && corsAllowCredentials) {
            corsConfigBuilder.allowCredentials();
        }
        corsConfigBuilder.allowNullOrigin();
        return corsConfigBuilder.build();
    }

    public PojoEndpointServer getPojoEndpointServer() {
        return pojoEndpointServer;
    }
}
