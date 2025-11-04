/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IStructType;

import java.util.Map;

public class GenericStructTypeImpl extends AbstractGenericType implements IStructType {
    private String typeName;
    private final Map<String, IGenericType> fieldTypes;
    private final IGenericType extFieldType;

    public GenericStructTypeImpl(Map<String, IGenericType> fieldTypes, IGenericType extFieldType) {
        this.fieldTypes = fieldTypes;
        this.extFieldType = extFieldType;
    }

    @Override
    public String getTypeName() {
        if (typeName == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (Map.Entry<String, IGenericType> entry : fieldTypes.entrySet()) {
                sb.append("\"").append(StringHelper.escapeJava(entry.getKey())).append("\"").append(':');
                sb.append(entry.getValue());
                sb.append(',');
            }
            if (extFieldType != null) {
                sb.append("[_:string]:");
                sb.append(extFieldType);
            }
            sb.append("}");
            typeName = sb.toString();
        }
        return typeName;
    }

    @Override
    public String getRawTypeName() {
        return getTypeName();
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.MAP;
    }

    @Override
    public GenericTypeKind getKind() {
        return GenericTypeKind.STRUCT;
    }

    @Override
    public Map<String, IGenericType> getFieldTypes() {
        return fieldTypes;
    }

    @Override
    public IGenericType getExtFieldType() {
        return extFieldType;
    }
}
