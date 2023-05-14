/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.IEqlQueryContext;
import io.nop.orm.model.IOrmDataType;

import java.util.List;

public class RenamedSqlExprMeta implements ISqlExprMeta {
    private final ISqlExprMeta sqlExprMeta;
    private final List<String> colNames;

    public RenamedSqlExprMeta(ISqlExprMeta sqlExprMeta, List<String> colNames) {
        this.sqlExprMeta = sqlExprMeta;
        this.colNames = colNames;
    }

    @Override
    public int getColumnCount() {
        return sqlExprMeta.getColumnCount();
    }

    @Override
    public List<IDataParameterBinder> getColumnBinders() {
        return sqlExprMeta.getColumnBinders();
    }

    @Override
    public List<String> getColumnNames() {
        return colNames;
    }

    @Override
    public IOrmDataType getOrmDataType() {
        return sqlExprMeta.getOrmDataType();
    }

    @Override
    public Object buildValue(Object[] row, int fromIndex, IEqlQueryContext session) {
        return sqlExprMeta.buildValue(row, fromIndex, session);
    }
}
