/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dataset.impl;

import io.nop.dao.dataset.IDataRow;
import io.nop.dao.dataset.IDataSetMeta;

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
}
