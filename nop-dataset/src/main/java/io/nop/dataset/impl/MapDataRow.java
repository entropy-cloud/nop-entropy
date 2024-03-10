/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSetMeta;

import java.util.HashMap;
import java.util.Map;

import static io.nop.dataset.DataSetErrors.ERR_DATASET_IS_READONLY;

public class MapDataRow implements IDataRow {
    private boolean readonly;
    private final IDataSetMeta meta;
    private final Map<String, Object> fields;

    public MapDataRow(IDataSetMeta meta, boolean readOnly, Map<String, Object> fields) {
        this.meta = meta;
        this.readonly = readOnly;
        this.fields = fields;
    }

    public MapDataRow(IDataSetMeta meta) {
        this(meta, false, new HashMap<>(meta.getFieldCount()));
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
        String name = meta.getFieldName(index);
        return fields.get(name);
    }

    @Override
    public void setObject(int index, Object value) {
        if (isReadonly())
            throw new NopException(ERR_DATASET_IS_READONLY);
        String name = meta.getFieldName(index);
        fields.put(name, value);
    }

    @Override
    public IDataSetMeta getMeta() {
        return meta;
    }

    @Override
    public int getFieldCount() {
        return meta.getFieldCount();
    }

    @Override
    public Object[] getFieldValues() {
        int n = getFieldCount();
        Object[] ret = new Object[n];
        for (int i = 0; i < n; i++) {
            Object value = getObject(i);
            ret[i] = value;
        }
        return ret;
    }

    @Override
    public IDataRow toDetachedDataRow() {
        return this;
    }
}