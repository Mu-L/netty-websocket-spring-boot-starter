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
import java.util.ArrayList;
import java.util.List;
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
     * 优雅关闭：分阶段完成，保证 {@code @OnClose} 不会因为线程池提前结束而被丢弃。可重复调用。
     * <p>
     * 顺序很关键：{@code useEventExecutorGroup=true} 时业务 handler 被 pin 到业务执行器上，
     * 若业务执行器先停，网络线程上的 {@code channelInactive} 会因执行器已终止而被拒绝
     * （{@code RejectedExecutionException: event executor terminated}），{@code @OnClose} 回调将被丢弃。
     * <p>
     * 注意 {@code shutdownGracefully()} 只是<em>发出</em>停止请求并立即返回，
     * 请求顺序并不等于线程池实际的终止顺序。因此每个阶段都等待
     * {@code terminationFuture()} 真正完成，而不是单纯按次序发出请求：
     * <ol>
     *     <li>关闭监听 socket，停止接受新连接；</li>
     *     <li>业务执行器仍在运行时关闭已完成握手的连接；</li>
     *     <li>请求停止 boss/worker 并<em>等待其真正终止</em>：worker 完全退出前仍可能向业务执行器
     *     投递事件（延迟转发的 {@code channelInactive}、握手完成后才挂载的 handler 等）；</li>
     *     <li>确认不会再有事件提交后，才请求停止业务执行器并等待它把已排队的回调排空。</li>
     * </ol>
     * 任一阶段超时会记录 ERROR，而不是当作正常停止。
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

        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_TIMEOUT_MILLIS);

        // 1) 停止接受新连接
        Channel channel = this.serverChannel;
        if (channel != null) {
            this.serverChannel = null;
            channel.close().awaitUninterruptibly();
        }

        // 2) 业务执行器仍在运行时关闭活跃连接，保证 @OnClose 能被正常投递
        if (!channels.isEmpty()) {
            channels.writeAndFlush(new CloseWebSocketFrame())
                    .awaitUninterruptibly(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
            awaitChannelGroupEmpty(deadlineNanos);
        }
        channels.close().awaitUninterruptibly(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);

        // 3) 网络线程池：发出停止请求后必须等到真正终止，否则业务执行器仍可能先于
        //    网络线程投递的事件结束，导致 @OnClose 丢失
        shutdownGracefully(this.boss);
        shutdownGracefully(this.worker);
        List<String> unfinished = new ArrayList<>(3);
        if (!awaitGroupTermination(this.boss, deadlineNanos)) {
            unfinished.add("boss");
        }
        if (!awaitGroupTermination(this.worker, deadlineNanos)) {
            unfinished.add("worker");
        }

        // 4) 网络线程已全部退出，不会再有新事件提交给业务执行器；
        //    此时才停止业务执行器，让它排空已经排队的 @OnClose 等回调
        shutdownGracefully(this.eventExecutorGroup);
        if (!awaitGroupTermination(this.eventExecutorGroup, deadlineNanos)) {
            unfinished.add("eventExecutorGroup");
        }

        if (unfinished.isEmpty()) {
            logger.info(String.format("\033[34mNetty WebSocket stopped on port: %s .\033[0m", config.getPort()));
        } else {
            logger.error(String.format(
                    "Netty WebSocket on port: %s stopped incompletely: %s did not terminate within %d ms, " +
                            "some @OnClose callbacks may have been dropped.",
                    config.getPort(), String.join(", ", unfinished), SHUTDOWN_TIMEOUT_MILLIS));
        }
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

    private void awaitChannelGroupEmpty(long deadlineNanos) {
        while (!channels.isEmpty() && System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void shutdownGracefully(EventExecutorGroup group) {
        if (group == null) {
            return;
        }
        group.shutdownGracefully(SHUTDOWN_QUIET_PERIOD_MILLIS, SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 等待线程池<em>真正终止</em>：{@code shutdownGracefully()} 只是发出停止请求，
     * 只有 {@code terminationFuture()} 完成才表示线程已退出、不会再有新的事件被投递出去。
     *
     * @return 在截止时间前终止返回 {@code true}，超时返回 {@code false}
     */
    private static boolean awaitGroupTermination(EventExecutorGroup group, long deadlineNanos) {
        if (group == null) {
            return true;
        }
        try {
            if (group.terminationFuture().await(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return group.isTerminated();
    }

    private static long remainingMillis(long deadlineNanos) {
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
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
