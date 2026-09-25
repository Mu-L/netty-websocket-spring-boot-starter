package org.yeauty.standard;

import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.support.ApplicationObjectSupport;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;
import org.yeauty.annotation.EnableWebSocket;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.exception.DeploymentException;
import org.yeauty.pojo.PojoEndpointServer;
import org.yeauty.pojo.PojoMethodMapping;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Yeauty
 */
public class ServerEndpointExporter extends ApplicationObjectSupport implements SmartInitializingSingleton, BeanFactoryAware, ResourceLoaderAware, DisposableBean {

    @Autowired
    Environment environment;

    private AbstractBeanFactory beanFactory;

    private ResourceLoader resourceLoader;

    private final Map<InetSocketAddress, WebsocketServer> addressWebsocketServerMap = new HashMap<>();

    /**
     * 已启动的服务，{@link #destroy()} 之后仍可据此查询线程池是否已终止。
     */
    private final List<WebsocketServer> startedServers = new ArrayList<>();

    @Override
    public void afterSingletonsInstantiated() {
        registerEndpoints();
    }

    /**
     * 容器关闭时优雅停止所有 Netty 服务，释放监听端口与线程池。
     */
    @Override
    public void destroy() {
        for (Map.Entry<InetSocketAddress, WebsocketServer> entry : addressWebsocketServerMap.entrySet()) {
            WebsocketServer websocketServer = entry.getValue();
            try {
                websocketServer.destroy();
                PojoEndpointServer pojoEndpointServer = websocketServer.getPojoEndpointServer();
                // 只有真正使用随机端口的服务才回收端口缓存，避免误清同一 host 上仍在运行的其他服务
                if (pojoEndpointServer.isRandomPort()) {
                    ServerEndpointConfig.clearRandomPort(pojoEndpointServer.getHost());
                }
            } catch (Exception e) {
                logger.error(String.format("websocket [%s] stop fail", entry.getKey()), e);
            }
        }
        addressWebsocketServerMap.clear();
    }

    /**
     * 等待所有已启动服务（含业务执行器）完全终止，用于验证关闭流程是否干净。
     *
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @return 全部终止返回 {@code true}，超时返回 {@code false}
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        boolean terminated = true;
        for (WebsocketServer websocketServer : startedServers) {
            long remaining = Math.max(1L, deadline - System.nanoTime());
            terminated &= websocketServer.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        }
        return terminated;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (!(beanFactory instanceof AbstractBeanFactory)) {
            throw new IllegalArgumentException(
                    "AutowiredAnnotationBeanPostProcessor requires a AbstractBeanFactory: " + beanFactory);
        }
        this.beanFactory = (AbstractBeanFactory) beanFactory;
    }

    protected void registerEndpoints() {
        ApplicationContext context = getApplicationContext();

        scanPackage(context);

        String[] endpointBeanNames = context.getBeanNamesForAnnotation(ServerEndpoint.class);
        Set<Class<?>> endpointClasses = new LinkedHashSet<>();
        for (String beanName : endpointBeanNames) {
            endpointClasses.add(context.getType(beanName));
        }

        for (Class<?> endpointClass : endpointClasses) {
            if (endpointClass.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
                registerEndpoint(endpointClass.getSuperclass());
            } else {
                registerEndpoint(endpointClass);
            }
        }

        init();
    }

    private void scanPackage(ApplicationContext context) {
        String[] basePackages = resolveBasePackages(context);

        if (basePackages == null || basePackages.length == 0) {
            logger.warn("No base package to scan for @ServerEndpoint beans: neither explicit scan base packages, " +
                    "nor a @SpringBootApplication bean, nor registered auto-configuration packages were found. " +
                    "Only @ServerEndpoint beans already registered in the context will be used.");
            return;
        }

        EndpointClassPathScanner scanHandle = new EndpointClassPathScanner((BeanDefinitionRegistry) context, false);
        if (resourceLoader != null) {
            scanHandle.setResourceLoader(resourceLoader);
        }

        for (String basePackage : basePackages) {
            scanHandle.doScan(basePackage);
        }
    }

    /**
     * 解析 {@code @ServerEndpoint} 的扫描包，按优先级依次尝试：
     * <ol>
     *     <li>{@code @EnableWebSocket(scanBasePackages)} 显式配置的包（优先级最高）；</li>
     *     <li>{@code @SpringBootApplication} 显式配置的包，否则取该注解所在包；</li>
     *     <li>{@link AutoConfigurationPackages} 注册的包（覆盖 {@code @SpringBootConfiguration + @EnableAutoConfiguration} 场景）；</li>
     * </ol>
     * 没有任何可用信息时返回 {@code null}，由调用方告警并跳过扫描，避免启动失败。
     */
    private String[] resolveBasePackages(ApplicationContext context) {
        String[] enableWebSocketBeanNames = context.getBeanNamesForAnnotation(EnableWebSocket.class);
        if (enableWebSocketBeanNames.length != 0) {
            for (String enableWebSocketBeanName : enableWebSocketBeanNames) {
                Object enableWebSocketBean = context.getBean(enableWebSocketBeanName);
                EnableWebSocket enableWebSocket = AnnotationUtils.findAnnotation(enableWebSocketBean.getClass(), EnableWebSocket.class);
                if (enableWebSocket != null && enableWebSocket.scanBasePackages().length != 0) {
                    return enableWebSocket.scanBasePackages();
                }
            }
        }

        String[] springBootApplicationBeanNames = context.getBeanNamesForAnnotation(SpringBootApplication.class);
        if (springBootApplicationBeanNames.length != 0) {
            Object springBootApplicationBean = context.getBean(springBootApplicationBeanNames[0]);
            SpringBootApplication springBootApplication = AnnotationUtils.findAnnotation(springBootApplicationBean.getClass(), SpringBootApplication.class);
            if (springBootApplication != null) {
                if (springBootApplication.scanBasePackages().length != 0) {
                    return springBootApplication.scanBasePackages();
                }
                return new String[]{ClassUtils.getPackageName(springBootApplicationBean.getClass().getName())};
            }
        }

        if (beanFactory != null) {
            try {
                List<String> autoConfigurationPackages = AutoConfigurationPackages.get(beanFactory);
                if (!autoConfigurationPackages.isEmpty()) {
                    return autoConfigurationPackages.toArray(new String[0]);
                }
            } catch (IllegalStateException ex) {
                logger.debug("No auto-configuration packages registered: " + ex.getMessage());
            }
        }

        return null;
    }

    private void init() {
        for (Map.Entry<InetSocketAddress, WebsocketServer> entry : addressWebsocketServerMap.entrySet()) {
            WebsocketServer websocketServer = entry.getValue();
            try {
                websocketServer.init();
                PojoEndpointServer pojoEndpointServer = websocketServer.getPojoEndpointServer();
                StringJoiner stringJoiner = new StringJoiner(",");
                pojoEndpointServer.getPathMatcherSet().forEach(pathMatcher -> stringJoiner.add("'" + pathMatcher.getPattern() + "'"));
                logger.info(String.format("\033[34mNetty WebSocket started on port: %s with context path(s): %s .\033[0m", pojoEndpointServer.getPort(), stringJoiner.toString()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while starting websocket server: " + entry.getKey(), e);
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to configure websocket TLS: " + entry.getKey(), e);

            } catch (DeploymentException e) {
                // 端口被占用等绑定失败必须让容器启动失败，否则会得到一个从未监听的服务
                logger.error(String.format("websocket [%s] bind fail", entry.getKey()), e);
                throw new IllegalStateException("Failed to start websocket server: " + e.getMessage(), e);
            }
        }
    }

    private void registerEndpoint(Class<?> endpointClass) {
        ServerEndpoint annotation = AnnotatedElementUtils.findMergedAnnotation(endpointClass, ServerEndpoint.class);
        if (annotation == null) {
            throw new IllegalStateException("missingAnnotation ServerEndpoint");
        }
        ServerEndpointConfig serverEndpointConfig = buildConfig(annotation);

        ApplicationContext context = getApplicationContext();
        PojoMethodMapping pojoMethodMapping = null;
        try {
            pojoMethodMapping = new PojoMethodMapping(endpointClass, context, beanFactory);
        } catch (DeploymentException e) {
            throw new IllegalStateException("Failed to register ServerEndpointConfig: " + serverEndpointConfig, e);
        }

        InetSocketAddress inetSocketAddress = new InetSocketAddress(serverEndpointConfig.getHost(), serverEndpointConfig.getPort());
        String path = resolveAnnotationValue(annotation.value(), String.class, "path");

        WebsocketServer websocketServer = addressWebsocketServerMap.get(inetSocketAddress);
        if (websocketServer == null) {
            PojoEndpointServer pojoEndpointServer = new PojoEndpointServer(pojoMethodMapping, serverEndpointConfig, path);
            websocketServer = new WebsocketServer(pojoEndpointServer, serverEndpointConfig);
            addressWebsocketServerMap.put(inetSocketAddress, websocketServer);
            startedServers.add(websocketServer);
        } else {
            websocketServer.getPojoEndpointServer().addPathPojoMethodMapping(path, pojoMethodMapping);
        }
    }

    private ServerEndpointConfig buildConfig(ServerEndpoint annotation) {
        String host = resolveAnnotationValue(annotation.host(), String.class, "host");
        int port = resolveAnnotationValue(annotation.port(), Integer.class, "port");
        String path = resolveAnnotationValue(annotation.value(), String.class, "value");
        int bossLoopGroupThreads = resolveAnnotationValue(annotation.bossLoopGroupThreads(), Integer.class, "bossLoopGroupThreads");
        int workerLoopGroupThreads = resolveAnnotationValue(annotation.workerLoopGroupThreads(), Integer.class, "workerLoopGroupThreads");
        boolean useCompressionHandler = resolveAnnotationValue(annotation.useCompressionHandler(), Boolean.class, "useCompressionHandler");

        int optionConnectTimeoutMillis = resolveAnnotationValue(annotation.optionConnectTimeoutMillis(), Integer.class, "optionConnectTimeoutMillis");
        int optionSoBacklog = resolveAnnotationValue(annotation.optionSoBacklog(), Integer.class, "optionSoBacklog");

        int childOptionWriteSpinCount = resolveAnnotationValue(annotation.childOptionWriteSpinCount(), Integer.class, "childOptionWriteSpinCount");
        int childOptionWriteBufferHighWaterMark = resolveAnnotationValue(annotation.childOptionWriteBufferHighWaterMark(), Integer.class, "childOptionWriteBufferHighWaterMark");
        int childOptionWriteBufferLowWaterMark = resolveAnnotationValue(annotation.childOptionWriteBufferLowWaterMark(), Integer.class, "childOptionWriteBufferLowWaterMark");
        int childOptionSoRcvbuf = resolveAnnotationValue(annotation.childOptionSoRcvbuf(), Integer.class, "childOptionSoRcvbuf");
        int childOptionSoSndbuf = resolveAnnotationValue(annotation.childOptionSoSndbuf(), Integer.class, "childOptionSoSndbuf");
        boolean childOptionTcpNodelay = resolveAnnotationValue(annotation.childOptionTcpNodelay(), Boolean.class, "childOptionTcpNodelay");
        boolean childOptionSoKeepalive = resolveAnnotationValue(annotation.childOptionSoKeepalive(), Boolean.class, "childOptionSoKeepalive");
        int childOptionSoLinger = resolveAnnotationValue(annotation.childOptionSoLinger(), Integer.class, "childOptionSoLinger");
        boolean childOptionAllowHalfClosure = resolveAnnotationValue(annotation.childOptionAllowHalfClosure(), Boolean.class, "childOptionAllowHalfClosure");

        int readerIdleTimeSeconds = resolveAnnotationValue(annotation.readerIdleTimeSeconds(), Integer.class, "readerIdleTimeSeconds");
        int writerIdleTimeSeconds = resolveAnnotationValue(annotation.writerIdleTimeSeconds(), Integer.class, "writerIdleTimeSeconds");
        int allIdleTimeSeconds = resolveAnnotationValue(annotation.allIdleTimeSeconds(), Integer.class, "allIdleTimeSeconds");

        int maxFramePayloadLength = resolveAnnotationValue(annotation.maxFramePayloadLength(), Integer.class, "maxFramePayloadLength");

        int maxMessagePayloadLength = resolveAnnotationValue(annotation.maxMessagePayloadLength(), Integer.class, "maxMessagePayloadLength");

        boolean useEventExecutorGroup = resolveAnnotationValue(annotation.useEventExecutorGroup(), Boolean.class, "useEventExecutorGroup");
        int eventExecutorGroupThreads = resolveAnnotationValue(annotation.eventExecutorGroupThreads(), Integer.class, "eventExecutorGroupThreads");

        String sslKeyPassword = resolveAnnotationValue(annotation.sslKeyPassword(), String.class, "sslKeyPassword");
        String sslKeyStore = resolveAnnotationValue(annotation.sslKeyStore(), String.class, "sslKeyStore");
        String sslKeyStorePassword = resolveAnnotationValue(annotation.sslKeyStorePassword(), String.class, "sslKeyStorePassword");
        String sslKeyStoreType = resolveAnnotationValue(annotation.sslKeyStoreType(), String.class, "sslKeyStoreType");
        String sslTrustStore = resolveAnnotationValue(annotation.sslTrustStore(), String.class, "sslTrustStore");
        String sslTrustStorePassword = resolveAnnotationValue(annotation.sslTrustStorePassword(), String.class, "sslTrustStorePassword");
        String sslTrustStoreType = resolveAnnotationValue(annotation.sslTrustStoreType(), String.class, "sslTrustStoreType");

        String[] corsOrigins = annotation.corsOrigins();
        if (corsOrigins.length != 0) {
            for (int i = 0; i < corsOrigins.length; i++) {
                corsOrigins[i] = resolveAnnotationValue(corsOrigins[i], String.class, "corsOrigins");
            }
        }
        Boolean corsAllowCredentials = resolveAnnotationValue(annotation.corsAllowCredentials(), Boolean.class, "corsAllowCredentials");

        ServerEndpointConfig serverEndpointConfig = new ServerEndpointConfig(host, port, bossLoopGroupThreads, workerLoopGroupThreads
                , useCompressionHandler, optionConnectTimeoutMillis, optionSoBacklog, childOptionWriteSpinCount, childOptionWriteBufferHighWaterMark
                , childOptionWriteBufferLowWaterMark, childOptionSoRcvbuf, childOptionSoSndbuf, childOptionTcpNodelay, childOptionSoKeepalive
                , childOptionSoLinger, childOptionAllowHalfClosure, readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds
                , maxFramePayloadLength, useEventExecutorGroup, eventExecutorGroupThreads
                , sslKeyPassword, sslKeyStore, sslKeyStorePassword, sslKeyStoreType
                , sslTrustStore, sslTrustStorePassword, sslTrustStoreType
                , corsOrigins, corsAllowCredentials, maxMessagePayloadLength);

        return serverEndpointConfig;
    }

    private <T> T resolveAnnotationValue(Object value, Class<T> requiredType, String paramName) {
        if (value == null) {
            return null;
        }
        TypeConverter typeConverter = beanFactory.getTypeConverter();

        if (value instanceof String) {
            String strVal = beanFactory.resolveEmbeddedValue((String) value);
            BeanExpressionResolver beanExpressionResolver = beanFactory.getBeanExpressionResolver();
            if (beanExpressionResolver != null) {
                value = beanExpressionResolver.evaluate(strVal, new BeanExpressionContext(beanFactory, null));
            } else {
                value = strVal;
            }
        }
        try {
            return typeConverter.convertIfNecessary(value, requiredType);
        } catch (TypeMismatchException e) {
            throw new IllegalArgumentException("Failed to convert value of parameter '" + paramName + "' to required type '" + requiredType.getName() + "'");
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
