/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.IEqlQueryContext;
import io.nop.orm.model.IOrmDataType;

import java.util.List;

/**
 * eql语句的select部分的每个字段对应一个{@link ISqlExprMeta}
 */
public interface ISqlExprMeta {
    /**
     * 需要从底层IDataRow中读取几列数据。例如如果是对应实体对象，则需要读取实体的eagerLoadProps所对应的数据列
     */
    int getColumnCount();

    List<IDataParameterBinder> getColumnBinders();

    List<String> getColumnNames();

    default StdDataType getStdDataType() {
        return getOrmDataType().getStdDataType();
    }

    default StdSqlType getStdSqlType() {
        return getOrmDataType().getStdSqlType();
    }

    IOrmDataType getOrmDataType();

    Object buildValue(Object[] row, int fromIndex, IEqlQueryContext session);
}