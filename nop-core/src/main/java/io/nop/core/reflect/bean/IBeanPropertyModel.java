/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.impl.AnnotationBeanModelBuilder;
import io.nop.core.type.IGenericType;

import java.lang.annotation.Annotation;

public interface IBeanPropertyModel {
    String getName();

    String getDescription();

    IGenericType getType();

    String getBizObjName();

    String getConfigVarName();

    boolean isLazyLoad();

    default Class<?> getRawClass() {
        return getType().getRawClass();
    }

    default String getRawTypeName() {
        return getType().getRawTypeName();
    }

    /**
     * 如果属性是Collection类型，则这里返回集合元素的泛型类型
     */
    default IGenericType getComponentType() {
        return getType().getComponentType();
    }

    /**
     * 是否复合对象类型，可以继续分解为属性集合
     */
    boolean isSimpleType();

    /**
     * 如果属性是Map类型，则这里返回Map的value的泛型类型
     */
    default IGenericType getMapValueType() {
        return getType().getMapValueType();
    }

    String getSerializer();

    String getDeserializer();

    boolean isSerializable();

    boolean isReadable();

    boolean isWritable();

    /**
     * 类声明中为字段设置的缺省值
     */
    Object getDefaultValue();

    JsonInclude.Include getJsonInclude();

    boolean isDeterministic();

    boolean shouldIncludeInSerialization(Object value);

    IPropertyGetter getGetter();

    IPropertySetter getSetter();

    IPropertyGetter getMaker();

    Object getPropertyValue(Object bean, IEvalScope scope);

    default Object getPropertyValue(Object bean) {
        return getPropertyValue(bean, DisabledEvalScope.INSTANCE);
    }

    void setPropertyValue(Object bean, Object value, IEvalScope scope);

    default void setPropertyValue(Object bean, Object value) {
        setPropertyValue(bean, value, DisabledEvalScope.INSTANCE);
    }

    Object makePropertyValue(Object bean, IEvalScope scope);

    <T extends Annotation> T  getAnnotation(Class<T> annotationClass);

    default Object makePropertyValue(Object bean) {
        return makePropertyValue(bean, DisabledEvalScope.INSTANCE);
    }
}