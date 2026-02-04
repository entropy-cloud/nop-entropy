/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IPropertySetter;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;

import java.util.HashSet;
import java.util.Set;

import static io.nop.ioc.IocErrors.ARG_PROP_NAME;

public class BeanProperty {
    private final SourceLocation location;
    private final IPropertySetter setter;
    private final IBeanPropValueResolver valueResolver;
    private final Class<?> targetClass;

    private final Set<String> configVars;

    private final boolean skipIfEmpty;

    private final boolean autowired;

    /**
     * 是否为延迟属性。延迟属性在init-method执行之后才设置
     */
    private final boolean lazyProperty;

    public BeanProperty(SourceLocation location, IPropertySetter setter, Class<?> targetClass,
                        IBeanPropValueResolver valueResolver, boolean autowired, boolean skipIfEmpty,
                        boolean lazyProperty) {
        this.location = location;
        this.setter = setter;
        this.targetClass = targetClass;
        this.valueResolver = Guard.notNull(valueResolver, "valueResolver");
        this.autowired = autowired;
        this.skipIfEmpty = skipIfEmpty;
        this.lazyProperty = lazyProperty;

        Set<String> configVars = new HashSet<>();
        valueResolver.collectConfigVars(configVars, true);
        this.configVars = configVars;
    }

    public boolean isAutowired() {
        return autowired;
    }

    public boolean isSkipIfEmpty() {
        return skipIfEmpty;
    }

    public boolean isLazyProperty() {
        return lazyProperty;
    }


    public void assignToObject(Object bean, String propName, IBeanContainerImplementor container,
                               IBeanScope beanScope) {
        Object value = valueResolver.resolveValue(container, beanScope);

        if (skipIfEmpty && StringHelper.isEmptyObject(value))
            return;

        Object converted = container.getClassIntrospection().convertTo(targetClass, value,
                err -> new NopException(err).loc(location).param(ARG_PROP_NAME, propName));

        this.setter.setProperty(bean, propName, converted, beanScope == null ? null : beanScope.getEvalScope());
    }

    public Set<String> getReactiveConfigVars() {
        return configVars;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public IPropertySetter getSetter() {
        return setter;
    }

    public IBeanPropValueResolver getValueResolver() {
        return valueResolver;
    }
}
