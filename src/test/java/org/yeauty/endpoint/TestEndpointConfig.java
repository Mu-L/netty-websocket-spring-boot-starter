package org.yeauty.endpoint;

import org.springframework.context.annotation.Configuration;
import org.yeauty.annotation.EnableWebSocket;

/**
 * 测试用配置：显式指定扫描包，避免依赖 {@code @SpringBootApplication}。
 */
@Configuration
@EnableWebSocket(scanBasePackages = "org.yeauty.endpoint")
public class TestEndpointConfig {
}
