/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.rowmapper;

import io.nop.dataset.IDataSet;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.impl.DefaultFieldMapper;

import java.util.function.Function;

public class RowMapperFirstExtractor<T> implements Function<IDataSet, T> {
    private final IRowMapper<T> rowMapper;
    private final IFieldMapper colMapper;

    public RowMapperFirstExtractor(IRowMapper<T> rowMapper, IFieldMapper colMapper) {
        this.rowMapper = rowMapper;
        this.colMapper = colMapper;
    }

    public RowMapperFirstExtractor(IRowMapper<T> rowMapper) {
        this(rowMapper, DefaultFieldMapper.INSTANCE);
    }

    @Override
    public T apply(IDataSet ds) {
        ds.setMaxRows(1);

        if (!ds.hasNext())
            return null;

        return rowMapper.mapRow(ds.next(), 1, colMapper);
    }
}