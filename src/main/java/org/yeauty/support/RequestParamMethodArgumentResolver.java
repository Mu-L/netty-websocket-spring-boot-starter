package org.yeauty.support;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.core.MethodParameter;
import org.yeauty.annotation.RequestParam;
import org.yeauty.exception.MissingRequestParameterException;

import java.util.List;
import java.util.Map;

import static org.yeauty.pojo.PojoEndpointServer.REQUEST_PARAM;

public class RequestParamMethodArgumentResolver implements MethodArgumentResolver {

    private static final String DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n";

    private final AbstractBeanFactory beanFactory;

    public RequestParamMethodArgumentResolver(AbstractBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestParam.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, Channel channel, Object object) throws Exception {
        RequestParam ann = parameter.getParameterAnnotation(RequestParam.class);
        String name = AnnotationNameSupport.resolve(ann, parameter);

        if (!channel.hasAttr(REQUEST_PARAM)) {
            QueryStringDecoder decoder = new QueryStringDecoder(((FullHttpRequest) object).uri());
            channel.attr(REQUEST_PARAM).set(decoder.parameters());
        }

        Map<String, List<String>> requestParams = channel.attr(REQUEST_PARAM).get();
        List<String> arg = (requestParams != null ? requestParams.get(name) : null);
        TypeConverter typeConverter = beanFactory.getTypeConverter();
        boolean hasDefault = !DEFAULT_NONE.equals(ann.defaultValue());
        boolean missing = arg == null || arg.isEmpty();
        boolean multiple = List.class.isAssignableFrom(parameter.getParameterType());
        Object value;
        if (hasDefault && (missing || (!multiple && arg.get(0).isEmpty()))) {
            value = ann.defaultValue();
        } else if (missing) {
            if (ann.required()) {
                throw new MissingRequestParameterException(name);
            }
            value = null;
        } else {
            value = multiple ? arg : arg.get(0);
        }
        Object converted = typeConverter.convertIfNecessary(value, parameter.getParameterType());
        if (converted == null && ann.required() && !hasDefault) {
            throw new MissingRequestParameterException(name);
        }
        return converted;
    }
}
