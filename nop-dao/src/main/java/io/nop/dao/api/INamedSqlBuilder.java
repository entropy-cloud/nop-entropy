package io.nop.dao.api;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.sql.SQL;

public interface INamedSqlBuilder {
    SQL buildSql(String sqlName, IEvalContext context);
}
