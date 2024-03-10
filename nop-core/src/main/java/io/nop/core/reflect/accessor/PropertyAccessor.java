/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.type.IGenericType;

@DataBean
public final class PropertyAccessor implements IPropertyGetter, IPropertySetter {
    private final String propName;
    private final IPropertyGetter getter;
    private final IPropertySetter setter;
    private final IPropertyGetter maker;
    private final IGenericType propType;

    public PropertyAccessor(@JsonProperty("propName") String propName, @JsonProperty("getter") IPropertyGetter getter,
                            @JsonProperty("setter") IPropertySetter setter, @JsonProperty("maker") IPropertyGetter maker,
                            @JsonProperty("propType") IGenericType propType) {
        this.propName = propName;
        this.getter = getter;
        this.setter = setter;
        this.maker = maker;
        this.propType = propType;
    }

    public static PropertyAccessor chain(PropertyAccessor parent, PropertyAccessor prop) {
        if (parent == null)
            return prop;

        String propName = buildChainedPropName(parent.getPropName(), prop.getPropName());

        ISpecializedPropertyGetter parentMaker = parent.getSpecializedMakerOrGetter();

        IPropertyGetter getter = null;
        IPropertyGetter maker = null;
        IPropertySetter setter = null;

        if (prop.getGetter() != null) {
            getter = new ChainedPropertyGetter(parentMaker, prop.getSpecializedGetter());
        }

        if (prop.getSetter() != null) {
            setter = new ChainedPropertySetter(parentMaker, prop.getSpecializedSetter());
        }

        if (prop.getMaker() != null) {
            maker = new ChainedPropertyGetter(parentMaker, prop.getSpecializedMaker());
        }

        return new PropertyAccessor(propName, getter, setter, maker, prop.getPropType());
    }

    public static String buildChainedPropName(String parent, String prop) {
        if (prop.startsWith("["))
            return parent + prop;
        return parent + "." + prop;
    }

    public String getPropName() {
        return propName;
    }

    public IGenericType getPropType() {
        return propType;
    }

    public ISpecializedPropertyGetter getSpecializedGetter() {
        if (getter == null)
            return null;
        if (getter instanceof ISpecializedPropertyGetter)
            return (ISpecializedPropertyGetter) getter;
        return PropertyAccessorAdapters.getByProp(getter, propName);
    }

    public ISpecializedPropertySetter getSpecializedSetter() {
        if (setter == null)
            return null;
        if (setter instanceof ISpecializedPropertySetter)
            return (ISpecializedPropertySetter) setter;
        return PropertyAccessorAdapters.setByProp(setter, propName);
    }

    public ISpecializedPropertyGetter getSpecializedMaker() {
        if (maker == null)
            return null;
        if (maker instanceof ISpecializedPropertyGetter)
            return (ISpecializedPropertyGetter) maker;
        return PropertyAccessorAdapters.getByProp(maker, propName);
    }

    public ISpecializedPropertyGetter getSpecializedMakerOrGetter() {
        ISpecializedPropertyGetter maker = getSpecializedMaker();
        if (maker == null)
            maker = getSpecializedGetter();
        return maker;
    }

    public boolean isReadable() {
        return getter != null;
    }

    public boolean isWritable() {
        return setter != null;
    }

    public IPropertyGetter getGetter() {
        return getter;
    }

    public IPropertySetter getSetter() {
        return setter;
    }

    public IPropertyGetter getMaker() {
        return maker;
    }

    public IPropertyGetter getMakerOrGetter() {
        if (maker != null)
            return maker;
        return getter;
    }

    public Object getProperty(Object obj, String name, IEvalScope scope) {
        Guard.notNull(getter, "getter");
        return getter.getProperty(obj, name, scope);
    }

    public void setProperty(Object obj, String name, Object value, IEvalScope scope) {
        Guard.notNull(setter, "setter");
        setter.setProperty(obj, name, value, scope);
    }

    public Object makePropertyValue(Object obj, String name, IEvalScope scope) {
        if (maker == null)
            return getProperty(obj, name, scope);
        return maker.getProperty(obj, name, scope);
    }
}