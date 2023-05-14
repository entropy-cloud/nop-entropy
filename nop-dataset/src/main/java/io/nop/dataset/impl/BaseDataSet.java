/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.impl;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.record.impl.BaseRecordInput;

import java.util.ArrayList;
import java.util.List;

public class BaseDataSet extends BaseRecordInput<IDataRow> implements IDataSet {
    public BaseDataSet(List<IDataRow> records, IDataSetMeta meta) {
        super(records, meta);
    }

    @Override
    public IDataSetMeta getMeta() {
        return (IDataSetMeta) super.getMeta();
    }

    @Override
    public boolean isDetached() {
        return true;
    }

    @Override
    public IDataSet detach() {
        return this;
    }

    public static BaseDataSet buildFrom(IDataSet ds) {
        List<IDataRow> records = new ArrayList<>();
        ds.forEach(row -> {
            records.add(row.toDetachedDataRow());
        });
        return new BaseDataSet(records, ds.getMeta());
    }
}