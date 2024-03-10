/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.rowmapper;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

import java.util.ArrayList;
import java.util.List;

public class ListStringRowMapper implements IRowMapper<List<String>> {
    public static ListStringRowMapper INSTANCE = new ListStringRowMapper();

    @Override
    public List<String> mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        int n = row.getFieldCount();
        List<String> ret = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String str = ConvertHelper.toString(colMapper.getValue(row, i));
            ret.add(str);
        }
        return ret;
    }
}