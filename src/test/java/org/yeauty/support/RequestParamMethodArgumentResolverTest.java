package org.yeauty.support;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.yeauty.annotation.RequestParam;
import org.yeauty.exception.MissingRequestParameterException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestParamMethodArgumentResolverTest {
    @Test void missingRequiredParameterHasUsefulError() {
        assertEquals("Required request parameter 'q' is missing",
                assertThrows(MissingRequestParameterException.class, () -> resolve("required", "/")).getMessage());
    }
    @Test void missingRequiredPrimitiveHasUsefulError() {
        assertThrows(MissingRequestParameterException.class, () -> resolve("primitive", "/"));
    }
    @Test void optionalParameterMayBeAbsent() throws Exception {
        assertNull(resolve("optional", "/"));
    }
    @Test void defaultAppliesWhenMissing() throws Exception {
        assertEquals("fallback", resolve("fallback", "/"));
    }
    @Test void defaultAppliesToEmptyScalar() throws Exception {
        assertEquals("fallback", resolve("fallback", "/?q="));
    }
    @Test void explicitEmptyDefaultIsSupported() throws Exception {
        assertEquals("", resolve("emptyDefault", "/"));
    }
    @Test void emptyStringWithoutDefaultRemainsPresent() throws Exception {
        assertEquals("", resolve("required", "/?q="));
    }
    @Test void conversionToNullStillChecksRequired() {
        assertThrows(MissingRequestParameterException.class, () -> resolve("number", "/?q="));
    }
    @Test void suppliedScalarOverridesDefault() throws Exception {
        assertEquals("hello", resolve("fallback", "/?q=hello"));
    }
    @Test void repeatedParametersRemainAList() throws Exception {
        assertEquals(List.of("one", "", "three"), resolve("multiple", "/?q=one&q=&q=three"));
    }
    @Test void numericDefaultIsConverted() throws Exception {
        assertEquals(42, resolve("numericDefault", "/?q="));
    }
    @Test void invalidNumberReportsConversionFailure() {
        assertThrows(TypeMismatchException.class, () -> resolve("number", "/?q=invalid"));
    }

    private Object resolve(String methodName, String uri) throws Exception {
        Method method = Arrays.stream(Sample.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        try {
            return new RequestParamMethodArgumentResolver(new DefaultListableBeanFactory())
                    .resolveArgument(new MethodParameter(method, 0), channel, request);
        } finally {
            request.release();
            channel.finishAndReleaseAll();
        }
    }

    static class Sample {
        void required(@RequestParam("q") String value) {}
        void primitive(@RequestParam("q") int value) {}
        void optional(@RequestParam(value="q", required=false) String value) {}
        void fallback(@RequestParam(value="q", defaultValue="fallback") String value) {}
        void emptyDefault(@RequestParam(value="q", defaultValue="") String value) {}
        void number(@RequestParam("q") Integer value) {}
        void numericDefault(@RequestParam(value="q", defaultValue="42") int value) {}
        void multiple(@RequestParam("q") List<String> values) {}
    }
}
