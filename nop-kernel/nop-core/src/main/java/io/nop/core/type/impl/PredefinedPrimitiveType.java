/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawType;

import java.util.Collections;
import java.util.List;

public class PredefinedPrimitiveType extends PredefinedGenericType implements IRawType {

    PredefinedPrimitiveType(StdDataType stdDataType, String predefinedName) {
        super(stdDataType.getMandatoryJavaTypeName(), stdDataType, predefinedName);
        Guard.checkArgument(stdDataType.isPrimitiveType(), "not primitive type");
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.RAW_TYPE;
    }

    public Class getRawClass() {
        return getStdDataType().getMandatoryJavaClass();
    }

    @Override
    public boolean isAssignableFrom(Class clazz) {
        StdDataType dataType = getStdDataType();
        return dataType.getJavaClass() == clazz || dataType.getMandatoryJavaClass() == clazz;
    }

    @Override
    public boolean isAssignableTo(Class clazz) {
        StdDataType dataType = getStdDataType();
        return dataType.getJavaClass() == clazz || dataType.getMandatoryJavaClass() == clazz;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isNumericType() {
        return getStdDataType().isNumericType();
    }

    @Override
    public List<IGenericType> getTypeParameters() {
        return Collections.emptyList();
    }

    @Override
    public IGenericType getSuperType() {
        return null;
    }

    @Override
    public List<IGenericType> getInterfaces() {
        return Collections.emptyList();
    }
}