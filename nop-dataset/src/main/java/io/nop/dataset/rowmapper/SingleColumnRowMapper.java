/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.rowmapper;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

public class SingleColumnRowMapper implements IRowMapper<Object> {
    public static SingleColumnRowMapper INSTANCE = new SingleColumnRowMapper();

    @Override
    public Object mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        return colMapper.getValue(row, 0);
    }
}
