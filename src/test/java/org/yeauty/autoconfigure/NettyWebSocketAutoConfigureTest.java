package org.yeauty.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yeauty.standard.ServerEndpointExporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 自动装配回归测试：验证 Spring Boot 4 升级后的自动配置入口在各类启动形态下的行为。
 */
class NettyWebSocketAutoConfigureTest {

    @Test
    @DisplayName("默认自动装配注册 ServerEndpointExporter")
    void exporterIsRegisteredByDefault() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(DefaultAutoConfiguration.class)) {
            ServerEndpointExporter exporter = context.getBean(ServerEndpointExporter.class);
            assertNotNull(exporter, "自动配置未注册 ServerEndpointExporter");
            assertEquals(1, context.getBeanNamesForType(ServerEndpointExporter.class).length,
                    "ServerEndpointExporter 不应重复注册");
        }
    }

    @Test
    @DisplayName("用户自定义 Exporter 时自动配置退让")
    void customExporterTakesPrecedence() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CustomExporterConfiguration.class)) {
            ServerEndpointExporter exporter = context.getBean(ServerEndpointExporter.class);
            assertSame(CustomExporterConfiguration.EXPORTER, exporter,
                    "用户自定义的 ServerEndpointExporter 应优先于自动配置");
        }
    }

    @Test
    @DisplayName("使用 @SpringBootConfiguration + @EnableAutoConfiguration 组合启动不抛异常")
    void comboAnnotationStartupDoesNotFail() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ComboAutoConfigurationApp.class)) {
            assertNotNull(context.getBean(ServerEndpointExporter.class),
                    "组合注解启动时应通过 AutoConfigurationPackages 完成装配");
            assertFalse(AutoConfigurationPackages.get(context).isEmpty(),
                    "组合注解启动应注册 AutoConfigurationPackages，否则扫描包解析会落到兜底分支");
        }
    }

    @Test
    @DisplayName("没有可用扫描包时仅跳过扫描而不抛异常")
    void missingBasePackagesIsTolerated() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(DefaultAutoConfiguration.class)) {
            // DefaultAutoConfiguration 不经由 @EnableAutoConfiguration，
            // 因此容器中没有 AutoConfigurationPackages，也没有 @SpringBootApplication bean。
            assertNotNull(context.getBean(ServerEndpointExporter.class),
                    "缺少扫描包信息时仍应正常启动");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(NettyWebSocketAutoConfigure.class)
    static class DefaultAutoConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(NettyWebSocketAutoConfigure.class)
    static class CustomExporterConfiguration {

        static final ServerEndpointExporter EXPORTER = new ServerEndpointExporter();

        @Bean
        ServerEndpointExporter serverEndpointExporter() {
            return EXPORTER;
        }
    }
}
