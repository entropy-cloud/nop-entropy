package io.nop.orm;

import io.nop.orm.eql.ICompiledSql;

public interface IOrmCachedQueryPlan {
    ICompiledSql getCompiledSql();
}
