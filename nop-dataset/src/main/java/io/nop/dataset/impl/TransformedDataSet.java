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

import java.io.IOException;
import java.util.function.Function;

public class TransformedDataSet implements IDataSet {
    private final IDataSet dataSet;
    private final IDataSetMeta meta;
    private final Function<IDataRow, IDataRow> transformer;

    public TransformedDataSet(IDataSet dataSet, IDataSetMeta meta, Function<IDataRow, IDataRow> transformer) {
        this.dataSet = dataSet;
        this.meta = meta;
        this.transformer = transformer;
    }

    @Override
    public long getReadCount() {
        return dataSet.getReadCount();
    }

    @Override
    public IDataSetMeta getMeta() {
        return meta;
    }

    @Override
    public boolean isDetached() {
        return dataSet.isDetached();
    }

    @Override
    public IDataSet detach() {
        return BaseDataSet.buildFrom(this);
    }

    @Override
    public void close() throws IOException {
        dataSet.close();
    }

    @Override
    public boolean hasNext() {
        return dataSet.hasNext();
    }

    @Override
    public IDataRow next() {
        return transformer.apply(dataSet.next());
    }
}
