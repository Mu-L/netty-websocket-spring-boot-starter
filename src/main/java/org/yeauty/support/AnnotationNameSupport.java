package org.yeauty.support;

import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.yeauty.annotation.PathVariable;
import org.yeauty.annotation.RequestParam;

/**
 * 解析 {@code @PathVariable} / {@code @RequestParam} 的绑定名。
 * <p>
 * 这两个注解的 {@code value} 与 {@code name} 通过 {@code @AliasFor} 互为别名，但解析器拿到的是
 * 未经合成的原始注解实例，因此这里统一按 {@code name() → value() → 参数名} 的顺序取值，
 * 保证 {@code @PathVariable("id")} 与 {@code @PathVariable(name = "id")} 行为一致。
 */
public final class AnnotationNameSupport {

    private AnnotationNameSupport() {
    }

    public static String resolve(PathVariable annotation, MethodParameter parameter) {
        String name = resolveFromAnnotation(annotation);
        if (StringUtils.hasText(name)) {
            return name;
        }
        return resolveFromParameter(parameter);
    }

    public static String resolve(RequestParam annotation, MethodParameter parameter) {
        String name = resolveFromAnnotation(annotation);
        if (StringUtils.hasText(name)) {
            return name;
        }
        return resolveFromParameter(parameter);
    }

    /**
     * 只读取注解上显式声明的名字，不包含参数名兜底，供 {@code supportsParameter} 判断是否聚合 Map。
     */
    public static String resolveFromAnnotation(PathVariable annotation) {
        return StringUtils.hasText(annotation.name()) ? annotation.name() : annotation.value();
    }

    /**
     * 只读取注解上显式声明的名字，不包含参数名兜底，供 {@code supportsParameter} 判断是否聚合 Map。
     */
    public static String resolveFromAnnotation(RequestParam annotation) {
        return StringUtils.hasText(annotation.name()) ? annotation.name() : annotation.value();
    }

    private static String resolveFromParameter(MethodParameter parameter) {
        String name = parameter.getParameterName();
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException(
                    "Name for argument type [" + parameter.getNestedParameterType().getName() +
                            "] not available, and parameter name information not found in class file either.");
        }
        return name;
    }
}
