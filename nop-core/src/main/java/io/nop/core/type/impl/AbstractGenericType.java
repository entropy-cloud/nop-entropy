/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type.impl;

import io.nop.api.core.json.IJsonString;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.Collections;
import java.util.List;

public abstract class AbstractGenericType implements IGenericType, IJsonString {

    public int hashCode() {
        // 假定getTypeName()中包含所有信息
        return getTypeName().hashCode();
    }

    public final String toString() {
        return getTypeName();
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IGenericType))
            return false;

        // predefined类型如果等价则一定是指针相等
        IGenericType other = (IGenericType) o;
        if (other.isPredefined())
            return false;

        return o.toString().equals(other.toString());
    }

    public Class<?> getRawClass() {
        return Object.class;
    }

    public void resolve(IRawTypeResolver resolver) {
        if (hasTypeParameter()) {
            getTypeParameters().forEach(type -> type.resolve(resolver));
        }
    }

    public IGenericType getSuperType() {
        return PredefinedGenericTypes.ANY_TYPE;
    }

    public List<IGenericType> getInterfaces() {
        return Collections.emptyList();
    }

    public IGenericType getGenericType(String typeName) {
        return null;
    }
}