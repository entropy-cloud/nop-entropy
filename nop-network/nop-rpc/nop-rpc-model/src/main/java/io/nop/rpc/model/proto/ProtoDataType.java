/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model.proto;

import io.nop.commons.type.BinaryScalarType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

public class ProtoDataType {
    /**
     * 当数据类型为map类型时，这里保存map的值类型
     */
    private ProtoDataType valueType;

    private BinaryScalarType scalarType;

    private String namedType;

    public static ProtoDataType makeMapType(ProtoDataType valueType) {
        ProtoDataType dataType = new ProtoDataType();
        dataType.setValueType(valueType);
        return dataType;
    }

    public static ProtoDataType makeScalarType(BinaryScalarType scalarType) {
        ProtoDataType dataType = new ProtoDataType();
        dataType.setScalarType(scalarType);
        return dataType;
    }

    public static ProtoDataType makeNamedType(String namedType) {
        ProtoDataType dataType = new ProtoDataType();
        dataType.setNamedType(namedType);
        return dataType;
    }

    public IGenericType toGenericType(boolean mandatory) {
        if (scalarType != null) {
            return PredefinedGenericTypes.getPredefinedType(scalarType.getJavaTypeName(mandatory));
        } else if (valueType != null) {
            return JavaGenericTypeBuilder.buildMapType(valueType.toGenericType(false));
        } else {
            IGenericType type = PredefinedGenericTypes.getPredefinedType(namedType);
            if (type != null)
                return type;
            return new GenericRawTypeReferenceImpl(namedType);
        }
    }

    public String getNamedType() {
        return namedType;
    }

    public void setNamedType(String namedType) {
        this.namedType = namedType;
    }

    public ProtoDataType getValueType() {
        return valueType;
    }

    public void setValueType(ProtoDataType valueType) {
        this.valueType = valueType;
    }

    public BinaryScalarType getScalarType() {
        return scalarType;
    }

    public void setScalarType(BinaryScalarType scalarType) {
        this.scalarType = scalarType;
    }
}
