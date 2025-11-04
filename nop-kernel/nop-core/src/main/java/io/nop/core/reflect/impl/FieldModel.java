/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;

public class FieldModel extends AnnotatedElement implements IFieldModel {
    private static final long serialVersionUID = -1819481164845774980L;
    private boolean extension;
    private String name;
    private Class rawClass = Object.class;
    private IGenericType type = PredefinedGenericTypes.ANY_TYPE;
    private Object defaultValue;
    private IPropertyGetter getter;
    private IPropertySetter setter;
    private int modifiers;

    public FieldModel() {
    }

    public FieldModel(String name, IGenericType type, IPropertyGetter getter, IPropertySetter setter) {
        this.name = name;
        this.getter = getter;
        setType(type);
        this.setter = setter;
    }

    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isExtension() {
        return extension;
    }

    public void setExtension(boolean extension) {
        checkReadonly();
        this.extension = extension;
    }

    public boolean isPrimitive() {
        return rawClass.isPrimitive();
    }

    @Override
    public boolean isReadable() {
        return getter != null;
    }

    @Override
    public boolean isWritable() {
        return setter != null;
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
    public IGenericType getType() {
        return type;
    }

    public void setType(IGenericType type) {
        checkReadonly();
        this.type = type;
        this.rawClass = type.getRawClass();
    }

    public Class getRawClass() {
        return rawClass;
    }

    public void setRawClass(Class rawClass) {
        checkReadonly();
        this.rawClass = rawClass;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(int modifiers) {
        checkReadonly();
        this.modifiers = modifiers;
    }

    @Override
    public IPropertyGetter getGetter() {
        return getter;
    }

    public void setGetter(IPropertyGetter getter) {
        checkReadonly();
        this.getter = getter;
    }

    @Override
    public IPropertySetter getSetter() {
        return setter;
    }

    public void setSetter(IPropertySetter setter) {
        checkReadonly();
        this.setter = setter;
    }
}