package io.nop.orm.factory;

import io.nop.orm.IOrmCachedQueryPlan;
import io.nop.orm.eql.ICompiledSql;

public class SimpleCachedQueryPlan implements IOrmCachedQueryPlan {
    private final ICompiledSql compiledSql;

    public SimpleCachedQueryPlan(ICompiledSql compiledSql) {
        this.compiledSql = compiledSql;
    }

    @Override
    public ICompiledSql getCompiledSql() {
        return compiledSql;
    }
}
