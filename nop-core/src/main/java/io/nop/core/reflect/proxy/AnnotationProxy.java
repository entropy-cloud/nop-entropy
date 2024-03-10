/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.proxy;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_METHOD_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_NOT_SUPPORTED_METHOD_FOR_SYNTHESIZED_ANNOTATION;

/**
 * 为Annotation接口生成一个代理实现类
 */
public class AnnotationProxy implements InvocationHandler {
    private final Class<? extends Annotation> type;
    private final Map<String, Object> data;
    private String string;
    private Integer hashCode;
    private IBeanModel beanModel;

    public AnnotationProxy(Class<? extends Annotation> type, Map<String, Object> data) {
        this.type = type;
        this.data = data == null ? Collections.emptyMap() : data;
        this.beanModel = ReflectionManager.instance().getBeanModelForClass(type);
    }

    public static <T extends Annotation> T getProxy(Class<T> type, Map<String, Object> data) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new AnnotationProxy(type, data));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (ReflectionHelper.isEqualsMethod(method)) {
            return annotationEquals(args[0]);
        }
        if (ReflectionHelper.isHashCodeMethod(method)) {
            return annotationHashCode();
        }
        if (ReflectionHelper.isToStringMethod(method)) {
            return this.toString();
        }
        if (isAnnotationTypeMethod(method)) {
            return this.type;
        }
        IBeanPropertyModel propModel = beanModel.getPropertyModel(method.getName());
        if (propModel != null) {
            return ConvertHelper.convertTo(propModel.getRawClass(), data.get(propModel.getName()), NopException::new);
        }
        throw new NopException(ERR_REFLECT_NOT_SUPPORTED_METHOD_FOR_SYNTHESIZED_ANNOTATION)
                .param(ARG_CLASS_NAME, type.getName()).param(ARG_METHOD_NAME, method.getName());
    }

    private boolean annotationEquals(Object o) {
        if (this == o) {
            return true;
        }
        if (!this.type.isInstance(o)) {
            return false;
        }
        for (IBeanPropertyModel propModel : beanModel.getPropertyModels().values()) {
            Object value = data.get(propModel.getName());
            Object otherValue = propModel.getPropertyValue(o);
            if (!Objects.equals(value, otherValue)) {
                return false;
            }
        }
        return true;
    }

    private int annotationHashCode() {
        if (hashCode == null) {
            hashCode = computeHashCode();
        }
        return hashCode;
    }

    private int computeHashCode() {
        int hashCode = 0;
        for (IBeanPropertyModel propModel : this.beanModel.getPropertyModels().values()) {
            Object value = data.get(propModel.getName());
            hashCode += (127 * propModel.getName().hashCode()) ^ ProxyHelper.metaHashCode(value);
        }
        return hashCode;
    }

    public String toString() {
        if (string == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("@").append(type.getName());
            sb.append("(");
            boolean first = true;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                sb.append(entry.getKey()).append('=');
                sb.append(ProxyHelper.metaToString(entry.getValue()));
            }
            sb.append(")");
            string = sb.toString();
        }
        return string;
    }

    private boolean isAnnotationTypeMethod(Method method) {
        return (method.getName().equals("annotationType") && method.getParameterCount() == 0);
    }
}