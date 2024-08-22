/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.loader.IQueryExecutor;
import io.nop.orm.model.IOrmModel;

import java.io.Serializable;

public interface IOrmSessionFactory extends AutoCloseable, IDialectProvider {

    /**
     * 清空所有查询数据缓存
     */
    void clearQueryCache();

    ITransactionTemplate txn();

    <T> T getExtension(String entityName, Class<T> extensionClass);

    /**
     * 清空指定查询缓存
     */
    void clearQueryCacheFor(String cacheName);

    void evictQueryCache(String cacheName, Serializable cacheKey);

    IJdbcTemplate getJdbcTemplate();

    IOrmSession openSession(boolean stateless);

    ICache<QueryPlanCacheKey, IOrmCachedQueryPlan> getQueryPlanCache();

    ICacheProvider getGlobalCache();

    IOrmModel getOrmModel();

    IShardSelector getShardSelector();

    IDialect getDialectForQuerySpace(String querySpace);

    void addInterceptor(IOrmInterceptor interceptor);

    void removeInterceptor(IOrmInterceptor interceptor);

    void addDaoListener(IOrmDaoListener daoListener);

    void removeDaoListener(IOrmDaoListener daoListener);

    ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete);

    ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                            IEqlAstTransformer astTransformer, boolean useCache,
                            boolean allowUnderscoreName, boolean enableFilter);

    IQueryExecutor getQueryExecutor(String querySpace);

    void reloadModel();
}