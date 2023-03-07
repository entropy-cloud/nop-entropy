/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dataset.rowmapper;

import io.nop.dao.dataset.IDataRow;
import io.nop.dao.dataset.IFieldMapper;
import io.nop.dao.dataset.IRowMapper;

public class ColRowMapper<T> implements IRowMapper<T> {
    private final IRowMapper<T> rowMapper;
    private final IFieldMapper colMapper;

    public ColRowMapper(IRowMapper<T> rowMapper, IFieldMapper colMapper) {
        this.rowMapper = rowMapper;
        this.colMapper = colMapper;
    }

    @Override
    public T mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        return rowMapper.mapRow(row, rowNumber, this.colMapper);
    }
}