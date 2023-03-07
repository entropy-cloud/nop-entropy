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

/**
 * 只有一列时返回第一列的数据，否则调用baseMapper将行包装为对象返回
 */
public class SmartRowMapper implements IRowMapper<Object> {
    public static SmartRowMapper INSTANCE = new SmartRowMapper(ColumnMapRowMapper.INSTANCE);
    public static SmartRowMapper CASE_SENSITIVE = new SmartRowMapper(ColumnMapRowMapper.CASE_SENSITIVE);

    public static SmartRowMapper CASE_INSENSITIVE = new SmartRowMapper(ColumnMapRowMapper.CASE_INSENSITIVE);

    public static SmartRowMapper CAMEL_CASE = new SmartRowMapper(ColumnMapRowMapper.CAMEL_CASE);

    private final IRowMapper<?> baseMapper;

    public SmartRowMapper(IRowMapper<?> baseMapper) {
        this.baseMapper = baseMapper;
    }

    @Override
    public Object mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        if (row.getFieldCount() == 1) {
            return colMapper.getValue(row, 0);
        }
        return baseMapper.mapRow(row, rowNumber, colMapper);
    }
}
