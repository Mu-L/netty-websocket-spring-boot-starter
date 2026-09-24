package org.yeauty.support;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.yeauty.annotation.RequestParam;

import java.util.List;
import java.util.Map;

import static org.yeauty.pojo.PojoEndpointServer.REQUEST_PARAM;

public class RequestParamMapMethodArgumentResolver implements MethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        return (requestParam != null && Map.class.isAssignableFrom(parameter.getParameterType()) &&
                !StringUtils.hasText(AnnotationNameSupport.resolveFromAnnotation(requestParam)));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, Channel channel, Object object) throws Exception {
        if (!channel.hasAttr(REQUEST_PARAM)) {
            QueryStringDecoder decoder = new QueryStringDecoder(((FullHttpRequest) object).uri());
            channel.attr(REQUEST_PARAM).set(decoder.parameters());
        }

        Map<String, List<String>> requestParams = channel.attr(REQUEST_PARAM).get();
        MultiValueMap multiValueMap = new LinkedMultiValueMap(requestParams);
        if (MultiValueMap.class.isAssignableFrom(parameter.getParameterType())) {
            return multiValueMap;
        } else {
            return multiValueMap.toSingleValueMap();
        }
    }
}
