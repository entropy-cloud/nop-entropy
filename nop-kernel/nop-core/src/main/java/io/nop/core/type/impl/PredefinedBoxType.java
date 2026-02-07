package io.nop.core.type.impl;

import io.nop.commons.type.StdDataType;

public class PredefinedBoxType extends PredefinedRawType{
    public PredefinedBoxType(String typeName, Class<?> rawClass, String predefinedName) {
        super(typeName, rawClass, predefinedName);
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
}
