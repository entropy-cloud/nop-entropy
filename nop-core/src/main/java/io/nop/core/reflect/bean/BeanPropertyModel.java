/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.ReadonlyModel;
import io.nop.core.reflect.IAnnotationSupport;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.type.IGenericType;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_PROP_NOT_READABLE;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_PROP_NOT_WRITABLE;

public class BeanPropertyModel extends ReadonlyModel implements IBeanPropertyModel {
    private String name;
    private IGenericType type;

    private String bizObjName;
    private boolean simpleType;
    private Object defaultValue;
    private String serializer;
    private String deserializer;
    private JsonInclude.Include jsonInclude;
    private IPropertyGetter getter;
    private IPropertySetter setter;
    private IPropertyGetter maker;
    private boolean field;
    private boolean serializable;
    private boolean deterministic;
    private boolean lazyLoad;

    private Boolean nullable;
    private String description;
    private String configVarName;

    public BeanPropertyModel() {
    }

    public BeanPropertyModel(String name, IGenericType type, IPropertyGetter getter, IPropertySetter setter) {
        this.name = name;
        this.setType(type);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    @Override
    public boolean isSimpleType() {
        return simpleType;
    }

    @Override
    public boolean isSerializable() {
        return serializable;
    }

    public void setSerializable(boolean serializable) {
        checkReadonly();
        this.serializable = serializable;
    }

    public String getConfigVarName() {
        return configVarName;
    }

    public void setConfigVarName(String configVarName) {
        this.configVarName = configVarName;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        checkReadonly();
        this.name = name;
    }

    @Override
    public String getBizObjName() {
        return bizObjName;
    }

    public void setBizObjName(String bizObjName) {
        checkReadonly();
        this.bizObjName = bizObjName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isField() {
        return field;
    }

    public void setField(boolean field) {
        checkReadonly();
        this.field = field;
    }

    @Override
    public IGenericType getType() {
        return type;
    }

    public void setType(IGenericType type) {
        checkReadonly();
        this.type = type;
        this.simpleType = type.getStdDataType().isSimpleType();
    }

    public boolean isLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(boolean lazyLoad) {
        checkReadonly();
        this.lazyLoad = lazyLoad;
    }

    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        checkReadonly();
        this.defaultValue = defaultValue;
    }

    @Override
    public String getSerializer() {
        return serializer;
    }

    public void setSerializer(String serializer) {
        checkReadonly();
        this.serializer = serializer;
    }

    @Override
    public String getDeserializer() {
        return deserializer;
    }

    public void setDeserializer(String deserializer) {
        checkReadonly();
        this.deserializer = deserializer;
    }

    @Override
    public boolean isReadable() {
        return getter != null;
    }

    @Override
    public boolean isWritable() {
        return setter != null;
    }

    public Include getJsonInclude() {
        return jsonInclude;
    }

    public void setJsonInclude(Include jsonInclude) {
        checkReadonly();
        this.jsonInclude = jsonInclude;
    }

    public IPropertyGetter getGetter() {
        return getter;
    }

    public void setGetter(IPropertyGetter getter) {
        checkReadonly();
        this.getter = getter;
    }

    public IPropertySetter getSetter() {
        return setter;
    }

    public void setSetter(IPropertySetter setter) {
        checkReadonly();
        this.setter = setter;
    }

    public IPropertyGetter getMaker() {
        return maker;
    }

    public void setMaker(IPropertyGetter maker) {
        checkReadonly();
        this.maker = maker;
    }

    @Override
    public boolean isDeterministic() {
        return deterministic;
    }

    public void setDeterministic(boolean deterministic) {
        checkReadonly();
        this.deterministic = deterministic;
    }

    @Override
    public boolean shouldIncludeInSerialization(Object value) {
        if (jsonInclude == Include.NON_NULL)
            return value != null;
        if (jsonInclude == Include.NON_EMPTY)
            return !StringHelper.isEmptyObject(value);
        if (jsonInclude == Include.NON_ABSENT) {
            if (value instanceof Optional) {
                return ((Optional) value).isPresent();
            }
            return false;
        }
        return true;
    }

    @Override
    public Object getPropertyValue(Object bean, IEvalScope scope) {
        if (!isReadable() || getter == null)
            throw new NopException(ERR_REFLECT_BEAN_PROP_NOT_READABLE).param(ARG_CLASS_NAME, bean.getClass().getName())
                    .param(ARG_PROP_NAME, name);
        return getter.getProperty(bean, getName(), scope);
    }

    @Override
    public void setPropertyValue(Object bean, Object value, IEvalScope scope) {
        if (!isWritable() || setter == null)
            throw new NopException(ERR_REFLECT_BEAN_PROP_NOT_WRITABLE).param(ARG_CLASS_NAME, bean.getClass().getName())
                    .param(ARG_PROP_NAME, name);
        setter.setProperty(bean, getName(), value, scope);
    }

    @Override
    public Object makePropertyValue(Object bean, IEvalScope scope) {
        if (maker != null)
            return maker.getProperty(bean, getName(), scope);
        return getPropertyValue(bean, scope);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (getter instanceof IAnnotationSupport) {
            T ann = ((IAnnotationSupport) getter).getAnnotation(annotationClass);
            if (ann != null)
                return ann;
        }
        if (setter instanceof IAnnotationSupport) {
            T ann = ((IAnnotationSupport) setter).getAnnotation(annotationClass);
            if (ann != null)
                return ann;
        }
        return null;
    }
}