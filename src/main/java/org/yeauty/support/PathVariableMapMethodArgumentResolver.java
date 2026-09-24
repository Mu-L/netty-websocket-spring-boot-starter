package org.yeauty.support;

import io.netty.channel.Channel;
import org.springframework.core.MethodParameter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.yeauty.annotation.PathVariable;

import java.util.Collections;
import java.util.Map;

import static org.yeauty.pojo.PojoEndpointServer.URI_TEMPLATE;

public class PathVariableMapMethodArgumentResolver implements MethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        PathVariable ann = parameter.getParameterAnnotation(PathVariable.class);
        return (ann != null && Map.class.isAssignableFrom(parameter.getParameterType()) &&
                !StringUtils.hasText(AnnotationNameSupport.resolveFromAnnotation(ann)));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, Channel channel, Object object) throws Exception {
        Map<String, String> uriTemplateVars = channel.attr(URI_TEMPLATE).get();
        if (!CollectionUtils.isEmpty(uriTemplateVars)) {
            return uriTemplateVars;
        } else {
            return Collections.emptyMap();
        }
    }
}
