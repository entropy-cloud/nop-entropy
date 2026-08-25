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

import java.util.Collections;
import java.util.Map;

import static io.nop.dataset.DataSetErrors.ERR_DATASET_IS_READONLY;

public class SingleColumnRow implements IDataRow {
    private final IDataSetMeta meta;
    private final Object value;

    public SingleColumnRow(IDataSetMeta meta, Object value) {
        this.meta = meta;
        this.value = value;
    }

    @Override
    public Object getObject(int index) {
        if (index == 0)
            return value;
        return null;
    }

    @Override
    public void setObject(int index, Object value) {
        // 与 BaseDataRow/MapDataRow 保持一致：只读行的写入必须抛异常，静默丢弃会让调用方得到假成功
        if (isReadonly())
            throw new NopException(ERR_DATASET_IS_READONLY);
    }

    @Override
    public IDataSetMeta getMeta() {
        return meta;
    }

    @Override
    public int getFieldCount() {
        return 1;
    }

    @Override
    public boolean isDetached() {
        return true;
    }

    @Override
    public boolean isReadonly() {
        return true;
    }

    @Override
    public void setReadonly(boolean readonly) {

    }

    @Override
    public Object[] getFieldValues() {
        return new Object[]{value};
    }

    @Override
    public IDataRow toDetachedDataRow() {
        return this;
    }

    @Override
    public Map<String, Object> toMap() {
        return Collections.singletonMap(meta.getFieldName(0), value);
    }
}
