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
import io.nop.dataset.binder.IDataParameterBinder;

public class SingleBinderRowMapper implements IRowMapper<Object> {
    private final IDataParameterBinder binder;

    public SingleBinderRowMapper(IDataParameterBinder binder) {
        this.binder = binder;
    }

    @Override
    public Object mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        return binder.getValue(row, 0);
    }
}
