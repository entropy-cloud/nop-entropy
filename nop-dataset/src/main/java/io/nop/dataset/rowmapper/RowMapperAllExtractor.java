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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class RowMapperAllExtractor<T> implements Function<IDataSet, List<T>> {
    private final IRowMapper<T> rowMapper;
    private final long maxCount;
    private final IFieldMapper colMapper;

    public RowMapperAllExtractor(IRowMapper<T> rowMapper, IFieldMapper colMapper, long maxCount) {
        this.rowMapper = rowMapper;
        this.maxCount = maxCount;
        this.colMapper = colMapper;
    }

    public RowMapperAllExtractor(IRowMapper<T> rowMapper, IFieldMapper colMapper) {
        this(rowMapper, colMapper, -1L);
    }

    public RowMapperAllExtractor(IRowMapper<T> rowMapper) {
        this(rowMapper, DefaultFieldMapper.INSTANCE);
    }

    @Override
    public List<T> apply(IDataSet ds) {
        if (!ds.hasNext())
            return new ArrayList<T>(0);

        List<T> ret = new ArrayList<T>();
        long index = 0;
        while ((maxCount < 0 || index < maxCount)) {
            T row = rowMapper.mapRow(ds.next(), index + 1, colMapper);
            index++;
            ret.add(row);
            if (!ds.hasNext())
                break;
        }
        return ret;
    }
}