/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.model.IOrmModel;

import java.io.Serializable;

public interface IOrmSessionFactory extends AutoCloseable, IDialectProvider {

    /**
     * 清空所有查询数据缓存
     */
    void clearQueryCache();

    /**
     * 清空指定查询缓存
     */
    void clearQueryCacheFor(String cacheName);

    void evictQueryCache(String cacheName, Serializable cacheKey);

    IOrmSession openSession(boolean stateless);

    ICache<QueryPlanCacheKey, ICompiledSql> getQueryPlanCache();

    ICacheProvider getGlobalCache();

    IOrmModel getOrmModel();

    <T> T getExtension(String entityName, Class<T> extensionClass);

    IShardSelector getShardSelector();

    IDialect getDialectForQuerySpace(String querySpace);

    void addInterceptor(IOrmInterceptor interceptor);

    void removeInterceptor(IOrmInterceptor interceptor);

    void addDaoListener(IOrmDaoListener daoListener);

    void removeDaoListener(IOrmDaoListener daoListener);

    ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete);
}