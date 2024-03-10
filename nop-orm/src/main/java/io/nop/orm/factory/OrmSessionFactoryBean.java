/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.util.Guard;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.cache.ICacheManagement;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.IoHelper;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.eql.meta.SqlExprMetaCache;
import io.nop.orm.loader.JdbcQueryExecutor;
import io.nop.orm.metrics.EmptyOrmMetricsImpl;
import io.nop.orm.metrics.OrmMetricsImpl;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.ICollectionPersister;
import io.nop.orm.persister.IEntityPersister;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.orm.OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_SIZE;
import static io.nop.orm.OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_TIMEOUT;
import static io.nop.orm.OrmConfigs.CFG_QUERY_PLAN_CACHE_SIZE;

/**
 * @author canonical_entropy@163.com
 */
public class OrmSessionFactoryBean extends SessionFactoryConfig implements IConfigRefreshable {
    static final Logger LOG = LoggerFactory.getLogger(OrmSessionFactoryBean.class);

    private SessionFactoryImpl sessionFactory;

    private ICacheManagement cacheManagement;

    @Override
    public void refreshConfig() {
        LocalCache<?, ?> queryPlanCache = (LocalCache<?, ?>) sessionFactory.getQueryPlanCache();
        queryPlanCache.getConfig().setMaximumSize(CFG_QUERY_PLAN_CACHE_SIZE.get());
        queryPlanCache.refreshConfig();

        if (getGlobalCache() == null) {
            LocalCacheProvider globalCacheProvider = (LocalCacheProvider) sessionFactory.getGlobalCache();
            globalCacheProvider.getConfig().setMaximumSize(CFG_ENTITY_GLOBAL_CACHE_SIZE.get());
            globalCacheProvider.getConfig().setExpireAfterWrite(CFG_ENTITY_GLOBAL_CACHE_TIMEOUT.get());
            globalCacheProvider.refreshConfig();
        }
    }

    @PostConstruct
    public void init() {
        LOG.info("orm.begin_init_session_factory");

        Guard.notNull(getJdbcTemplate(), "jdbcTemplate");
        Guard.notNull(getBeanProvider(), "beanProvider");
        Guard.notNull(getShardSelector(), "shardSelector");
        Guard.notNull(getSequenceGenerator(), "sequenceGenerator");

        SessionFactoryImpl impl = new SessionFactoryImpl();
        impl.setJdbcTemplate(getJdbcTemplate());
        impl.setBeanProvider(getBeanProvider());
        impl.setShardSelector(getShardSelector());
        impl.setDefaultQueryExecutor(getDefaultQueryExecutor());
        impl.setGlobalCache(getGlobalCache());
        impl.setDialectProvider(getDialectProvider());
        impl.setEntityClassLoader(getEntityClassLoader());
        impl.setDefaultDynamicEntityClass(getDefaultDynamicEntityClass());
        impl.setDynamicEntityNames(getDynamicEntityNames());
        impl.setColumnBinderEnhancer(getColumnBinderEnhancer());
        impl.setEqlAstTransformer(getEqlAstTransformer());
        impl.setInterceptors(getInterceptors());

        impl.setQueryPlanCache(LocalCache.newCache(buildFullName("orm-query-plan-cache"),
                newConfig(CFG_QUERY_PLAN_CACHE_SIZE.get()).useMetrics()));

        if (this.getDialectProvider() == null) {
            impl.setDialectProvider(getJdbcTemplate());
        }

        if (this.getGlobalCache() == null) {
            CacheConfig config = new CacheConfig();
            config.setMaximumSize(CFG_ENTITY_GLOBAL_CACHE_SIZE.get());
            config.setExpireAfterWrite(CFG_ENTITY_GLOBAL_CACHE_TIMEOUT.get());
            impl.setGlobalCache(new LocalCacheProvider(buildFullName("orm-global-cache"), config));
        }

        if (this.getEntityClassLoader() == null)
            this.setEntityClassLoader(clazz -> ClassHelper.getDefaultClassLoader().loadClass(clazz));

        if (this.getDefaultQueryExecutor() == null)
            impl.setDefaultQueryExecutor(new JdbcQueryExecutor(impl));

        if (isUseMetrics()) {
            impl.setOrmMetrics(new OrmMetricsImpl(GlobalMeterRegistry.instance(), null));
        } else {
            impl.setOrmMetrics(new EmptyOrmMetricsImpl());
        }

        this.sessionFactory = impl;
        reloadOrmModel();

        if (this.isRegisterGlobalCache()) {
            cacheManagement = new OrmSessionFactoryCacheManagement();
            GlobalCacheRegistry.instance().register(cacheManagement);
        }

        LOG.info("orm.init_session_factory");
    }

    class OrmSessionFactoryCacheManagement implements ICacheManagement {
        @Override
        public String getName() {
            return OrmSessionFactoryBean.this.getName() + "-session-factory-cache";
        }

        @Override
        public void remove(@Nonnull Object key) {
            clearCache();
        }

        @Override
        public void clear() {
            clearCache();
        }

        @Override
        public Object stats() {
            return null;
        }
    }

    public void clearCache() {
        IOrmSessionFactory sessionFactory = this.sessionFactory;
        if (sessionFactory != null) {
            // 查询结果缓存
            sessionFactory.clearQueryCache();
            // 全局实体缓存
            sessionFactory.getGlobalCache().clearAllCache();
            // EQL编译缓存
            sessionFactory.getQueryPlanCache().clear();
        }
    }

    public void reloadOrmModel() {
        IOrmModel ormModel = getOrmModel();
        if (ormModel == null) {
            ormModel = new OrmModelLoader().loadOrmModel();
        }

        PersistEnvBuilder builder = new PersistEnvBuilder(ormModel, getSequenceGenerator(), sessionFactory);
        builder.build();
        Map<String, IEntityPersister> entityPersisters = builder.getEntityPersisters();
        Map<String, ICollectionPersister> collectionPersisters = builder.getCollectionPersisters();

        SqlExprMetaCache sqlExprMetaCache = new SqlExprMetaCache(sessionFactory.getColumnBinderEnhancer(),
                sessionFactory.getDialectProvider(), ormModel);

        sessionFactory.setEntityPersisters(entityPersisters);
        sessionFactory.setCollectionPersisters(collectionPersisters);
        sessionFactory.setOrmModel(ormModel);
        sessionFactory.setSequenceGenerator(getSequenceGenerator());
        sessionFactory.setSqlExprMetaCache(sqlExprMetaCache);
        sessionFactory.setReloadFunction(this::reloadOrmModel);
    }

    @PreDestroy
    public void destroy() {
        LOG.info("nop.orm.destroy-session-factory");
        if (cacheManagement != null) {
            GlobalCacheRegistry.instance().unregister(cacheManagement);
        }
        if (sessionFactory != null) {
            IoHelper.safeCloseObject(sessionFactory);
        }
    }

    public IOrmSessionFactory getObject() {
        return sessionFactory;
    }
}