/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSetMeta;

import static io.nop.dataset.DataSetErrors.ERR_DATASET_IS_READONLY;

public class BaseDataRow implements IDataRow {
    private boolean readonly;
    private final IDataSetMeta meta;
    private final Object[] fields;

    public BaseDataRow(IDataSetMeta meta, boolean readOnly, Object[] fields) {
        this.meta = meta;
        this.readonly = readOnly;
        this.fields = fields;
    }

    public BaseDataRow(IDataSetMeta meta) {
        this(meta, false, new Object[meta.getFieldCount()]);
    }

    @Override
    public boolean isDetached() {
        return true;
    }

    @Override
    public boolean isReadonly() {
        return readonly;
    }

    @Override
    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    @Override
    public Object getObject(int index) {
        return fields[index];
    }

    @Override
    public void setObject(int index, Object value) {
        if (isReadonly())
            throw new NopException(ERR_DATASET_IS_READONLY);
        fields[index] = value;
    }

    @Override
    public IDataSetMeta getMeta() {
        return meta;
    }

    @Override
    public int getFieldCount() {
        return fields.length;
    }

    @Override
    public Object[] getFieldValues() {
        return fields.clone();
    }

    @Override
    public IDataRow toDetachedDataRow() {
        return this;
    }
}