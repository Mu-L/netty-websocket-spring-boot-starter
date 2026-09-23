package org.yeauty.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.yeauty.annotation.PathVariable;
import org.yeauty.annotation.RequestParam;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 参数名解析的直接单测：{@code @PathVariable} / {@code @RequestParam} 的 {@code value} 与 {@code name}
 * 互为别名，两种写法必须解析出相同的结果。
 */
class AnnotationNameSupportTest {

    @Test
    @DisplayName("PathVariable 的 value 与 name 等价")
    void pathVariableValueAndNameAreEquivalent() throws Exception {
        assertEquals("id", resolvePathVariable("valueAlias"));
        assertEquals("id", resolvePathVariable("nameAlias"));
    }

    @Test
    @DisplayName("RequestParam 的 value 与 name 等价")
    void requestParamValueAndNameAreEquivalent() throws Exception {
        assertEquals("token", resolveRequestParam("requestValueAlias"));
        assertEquals("token", resolveRequestParam("requestNameAlias"));
    }

    @Test
    @DisplayName("注解未声明名字时回退到参数名")
    void parameterNameIsUsedWhenAnnotationIsEmpty() throws Exception {
        assertEquals("id", resolvePathVariable("noExplicitName"));
    }

    @Test
    @DisplayName("只读取注解名字时不使用参数名兜底")
    void annotationOnlyResolutionIgnoresParameterName() throws Exception {
        assertEquals("id", AnnotationNameSupport.resolveFromAnnotation(
                parameter("valueAlias").getParameterAnnotation(PathVariable.class)));
        assertEquals("id", AnnotationNameSupport.resolveFromAnnotation(
                parameter("nameAlias").getParameterAnnotation(PathVariable.class)));
        assertEquals("", AnnotationNameSupport.resolveFromAnnotation(
                parameter("map").getParameterAnnotation(PathVariable.class)));
        assertEquals("", AnnotationNameSupport.resolveFromAnnotation(
                parameter("requestMap").getParameterAnnotation(RequestParam.class)));
    }

    private static String resolvePathVariable(String methodName) throws Exception {
        MethodParameter parameter = parameter(methodName);
        return AnnotationNameSupport.resolve(parameter.getParameterAnnotation(PathVariable.class), parameter);
    }

    private static String resolveRequestParam(String methodName) throws Exception {
        MethodParameter parameter = parameter(methodName);
        return AnnotationNameSupport.resolve(parameter.getParameterAnnotation(RequestParam.class), parameter);
    }

    private static MethodParameter parameter(String methodName) throws Exception {
        MethodParameter parameter = new MethodParameter(Sample.class.getDeclaredMethod(methodName, parameterType(methodName)), 0);
        parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        return parameter;
    }

    private static Class<?> parameterType(String methodName) {
        if ("map".equals(methodName) || "requestMap".equals(methodName)) {
            return Map.class;
        }
        return String.class;
    }

    @SuppressWarnings("unused")
    static class Sample {

        public void valueAlias(@PathVariable("id") String id) {
        }

        public void nameAlias(@PathVariable(name = "id") String id) {
        }

        public void requestValueAlias(@RequestParam("token") String token) {
        }

        public void requestNameAlias(@RequestParam(name = "token") String token) {
        }

        public void noExplicitName(@PathVariable String id) {
        }

        public void map(@PathVariable Map<String, String> vars) {
        }

        public void requestMap(@RequestParam Map<String, String> params) {
        }
    }
}
