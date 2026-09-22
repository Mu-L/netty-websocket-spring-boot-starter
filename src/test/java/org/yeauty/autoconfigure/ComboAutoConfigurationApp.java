package org.yeauty.autoconfigure;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 测试用启动类：只使用 {@code @SpringBootConfiguration + @EnableAutoConfiguration}，
 * 不出现 {@code @SpringBootApplication}，也不声明任何 {@code @ServerEndpoint}。
 * <p>
 * 用于守护该组合注解启动路径不再抛出 {@code ArrayIndexOutOfBoundsException}。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class ComboAutoConfigurationApp {
}
