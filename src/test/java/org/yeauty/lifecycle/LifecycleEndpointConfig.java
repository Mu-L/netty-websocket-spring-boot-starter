package org.yeauty.lifecycle;

import org.springframework.context.annotation.Configuration;
import org.yeauty.annotation.EnableWebSocket;

/**
 * 生命周期测试专用配置：只扫描 {@code org.yeauty.lifecycle}，
 * 与集成测试的 {@link org.yeauty.endpoint.TestEndpointConfig} 完全隔离。
 */
@Configuration
@EnableWebSocket(scanBasePackages = "org.yeauty.lifecycle")
public class LifecycleEndpointConfig {
}
