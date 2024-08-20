package io.nop.orm.factory;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.orm.IOrmCachedQueryPlan;
import io.nop.orm.eql.ICompiledSql;

public class TenantCachedQueryPlan implements IOrmCachedQueryPlan {
    private ICompiledSql compiledSql;

    private final ICache<String, ICompiledSql> tenantCache = LocalCache.newCache(null, CacheConfig.newConfig(100));

    @Override
    public ICompiledSql getCompiledSql() {
        String tenantId = ContextProvider.currentTenantId();
        if (tenantId != null)
            return tenantCache.get(tenantId);
        return compiledSql;
    }

    public void addCompiledSql(ICompiledSql compiledSql) {
        String tenantId = ContextProvider.currentTenantId();
        if (tenantId == null) {
            this.compiledSql = compiledSql;
        } else {
            this.tenantCache.put(tenantId, compiledSql);
        }
    }
}