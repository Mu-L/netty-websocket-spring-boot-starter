package org.yeauty.support;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.core.MethodParameter;
import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.PathVariable;
import org.yeauty.annotation.RequestParam;

public class TextMethodArgumentResolver implements MethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getMethod().isAnnotationPresent(OnMessage.class)
                && String.class.isAssignableFrom(parameter.getParameterType())
                && !parameter.hasParameterAnnotation(PathVariable.class)
                && !parameter.hasParameterAnnotation(RequestParam.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, Channel channel, Object object) throws Exception {
        TextWebSocketFrame textFrame = (TextWebSocketFrame) object;
        return textFrame.text();
    }
}
