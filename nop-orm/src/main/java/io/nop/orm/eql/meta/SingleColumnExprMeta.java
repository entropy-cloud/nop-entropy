/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.api.core.util.Guard;
import io.nop.core.lang.sql.StdSqlType;
import io.nop.core.lang.sql.binder.DataParameterBinders;
import io.nop.core.lang.sql.binder.IDataParameterBinder;
import io.nop.orm.model.ExprOrmDataType;
import io.nop.orm.model.IOrmDataType;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SingleColumnExprMeta implements ISqlExprMeta {
    private final List<IDataParameterBinder> binders;
    private final IOrmDataType dataType;
    private final List<String> columnNames;

    public SingleColumnExprMeta(String columnName, IDataParameterBinder binder, IOrmDataType dataType) {
        Guard.notNull(binder, "binder");
        Guard.notNull(binder, "dataType");
        this.columnNames = columnName == null ? null : Collections.singletonList(columnName);
        this.binders = Collections.singletonList(binder);
        this.dataType = dataType;
    }

    @Override
    public List<String> getColumnNames() {
        return columnNames;
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public List<IDataParameterBinder> getColumnBinders() {
        return binders;
    }

    @Override
    public IOrmDataType getOrmDataType() {
        return dataType;
    }

    @Override
    public Object buildValue(Object[] row, int fromIndex, IOrmSessionImplementor session) {
        return row[fromIndex];
    }

    static final Map<StdSqlType, ISqlExprMeta> s_meta = new HashMap<>();

    static {
        for (StdSqlType type : StdSqlType.values()) {
            IOrmDataType dataType = ExprOrmDataType.fromSqlType(type);
            IDataParameterBinder binder = DataParameterBinders.getDefaultBinder(type.getName());
            if (binder == null)
                binder = DataParameterBinders.ANY;
            s_meta.put(type, new SingleColumnExprMeta(null, binder, dataType));
        }
    }

    public static ISqlExprMeta valueExprMeta(StdSqlType type) {
        return s_meta.get(type);
    }
}