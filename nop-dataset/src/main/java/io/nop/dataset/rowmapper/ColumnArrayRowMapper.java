/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.rowmapper;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

public class ColumnArrayRowMapper implements IRowMapper<Object[]> {
    public static ColumnArrayRowMapper INSTANCE = new ColumnArrayRowMapper();

    @Override
    public Object[] mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        int columnCount = row.getFieldCount();
        Object[] ret = new Object[columnCount];
        for (int i = 0; i < columnCount; i++) {
            Object obj = colMapper.getValue(row, i);
            ret[i] = obj;
        }
        return ret;
    }
}