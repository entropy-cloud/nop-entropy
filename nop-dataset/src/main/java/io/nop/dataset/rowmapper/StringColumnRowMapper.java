/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.rowmapper;

import io.nop.commons.util.StringHelper;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

public class StringColumnRowMapper implements IRowMapper<String> {
    public static StringColumnRowMapper INSTANCE = new StringColumnRowMapper();

    @Override
    public String mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        return StringHelper.toString(colMapper.getValue(row, 0), null);
    }
}