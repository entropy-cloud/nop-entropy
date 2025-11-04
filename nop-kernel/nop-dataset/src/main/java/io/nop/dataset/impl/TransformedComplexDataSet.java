/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;

import java.util.function.Function;

public class TransformedComplexDataSet implements IComplexDataSet {
    private final IComplexDataSet ds;
    private final IDataSetMeta meta;
    private final Function<IDataRow, IDataRow> transformer;

    public TransformedComplexDataSet(IComplexDataSet ds, IDataSetMeta meta, Function<IDataRow, IDataRow> transformer) {
        this.ds = ds;
        this.meta = meta;
        this.transformer = transformer;
    }

    @Override
    public IDataSet getResultSet() {
        return new TransformedDataSet(ds.getResultSet(), meta, transformer);
    }

    @Override
    public long getUpdateCount() {
        return ds.getUpdateCount();
    }

    @Override
    public long getReadCount() {
        return ds.getReadCount();
    }

    @Override
    public boolean getMoreResults() {
        return ds.getMoreResults();
    }

    @Override
    public boolean isResultSet() {
        return ds.isResultSet();
    }

    @Override
    public void setMaxRows(long maxRows) {
        ds.setMaxRows(maxRows);
    }

    @Override
    public void setFetchSize(int fetchSize) {
        ds.setFetchSize(fetchSize);
    }

    @Override
    public void cancel() {
        ds.cancel();
    }

    @Override
    public void close() throws Exception {
        ds.close();
    }
}
