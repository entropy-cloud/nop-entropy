/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dataset.IDataFieldMeta;

public class RenameDataFieldMeta implements IDataFieldMeta {
    private final String name;

    private final IDataFieldMeta fieldMeta;

    public RenameDataFieldMeta(String name, IDataFieldMeta fieldMeta) {
        this.name = name;
        this.fieldMeta = fieldMeta;
    }

    @Override
    public String getFieldName() {
        return name;
    }

    @Override
    public String getSourceFieldName() {
        return fieldMeta.getSourceFieldName();
    }

    @Override
    public String getFieldOwnerEntityName() {
        return fieldMeta.getFieldOwnerEntityName();
    }

    @Override
    public StdDataType getStdDataType() {
        return fieldMeta.getStdDataType();
    }

    @Override
    public StdSqlType getStdSqlType() {
        return fieldMeta.getStdSqlType();
    }

    @Override
    public boolean isComputed() {
        return fieldMeta.isComputed();
    }
}