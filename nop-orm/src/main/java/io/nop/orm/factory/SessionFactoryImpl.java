/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.lang.IClassLoader;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanConstructor;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.shard.EmptyShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmCachedQueryPlan;
import io.nop.orm.IOrmComponent;
import io.nop.orm.IOrmDaoListener;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.IOrmSession;
import io.nop.orm.QueryPlanCacheKey;
import io.nop.orm.driver.ICollectionPersistDriver;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.driver.jdbc.JdbcCollectionPersistDriver;
import io.nop.orm.driver.jdbc.JdbcEntityPersistDriver;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.binder.IOrmColumnBinderEnhancer;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.impl.MultiOrmDaoListener;
import io.nop.orm.loader.IQueryExecutor;
import io.nop.orm.metrics.IOrmMetrics;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.session.OrmSessionImpl;
import io.nop.orm.sql.IEntityFilterProvider;
import io.nop.orm.support.DynamicOrmEntity;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.orm.OrmErrors.ARG_BEAN_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_BEAN_NOT_PROTOTYPE_SCOPE;

/**
 * @author canonical_entropy@163.com
 */
public class SessionFactoryImpl implements IPersistEnv {
    static final Logger LOG = LoggerFactory.getLogger(SessionFactoryImpl.class);

    private IJdbcTemplate jdbcTemplate;

    private IShardSelector shardSelector = EmptyShardSelector.INSTANCE;

    private ICacheProvider globalCache;

    private ICache<QueryPlanCacheKey, IOrmCachedQueryPlan> queryPlanCache;

    private IQueryExecutor defaultQueryExecutor;

    private IBeanProvider beanProvider;

    private IClassLoader entityClassLoader;

    private IEntityFilterProvider entityFilterProvider;

    private List<IOrmInterceptor> interceptors = Collections.emptyList();

    private MultiOrmDaoListener daoListeners = null;

    private Map<String, IQueryExecutor> queryExecutors = Collections.emptyMap();

    private IDialectProvider dialectProvider;

    private IOrmMetrics ormMetrics;

    private Class<?> defaultDynamicEntityClass = DynamicOrmEntity.class;
    private Set<String> dynamicEntityNames = Collections.emptySet();

    private IOrmColumnBinderEnhancer columnBinderEnhancer;

    private IEqlAstTransformer defaultAstTransformer;

    private IOrmModelHolder ormModelHolder;

    private ISequenceGenerator sequenceGenerator;

    @Override
    public ISequenceGenerator getSequenceGenerator() {
        return sequenceGenerator;
    }

    public void setSequenceGenerator(ISequenceGenerator sequenceGenerator) {
        this.sequenceGenerator = sequenceGenerator;
    }

    public IOrmColumnBinderEnhancer getColumnBinderEnhancer() {
        return columnBinderEnhancer;
    }

    public void setDefaultAstTransformer(IEqlAstTransformer defaultAstTransformer) {
        this.defaultAstTransformer = defaultAstTransformer;
    }

    public IEqlAstTransformer getDefaultAstTransformer() {
        return defaultAstTransformer;
    }

    public void setEntityFilterProvider(IEntityFilterProvider entityFilterProvider) {
        this.entityFilterProvider = entityFilterProvider;
    }

    public void setOrmModelHolder(IOrmModelHolder ormModelHolder) {
        this.ormModelHolder = ormModelHolder;
    }

    @Override
    public ILoadedOrmModel getLoadedOrmModel() {
        return ormModelHolder.getOrmModel(this);
    }

    @Override
    public IEntityFilterProvider getEntityFilterProvider() {
        return entityFilterProvider;
    }

    @Inject
    public void setColumnBinderEnhancer(IOrmColumnBinderEnhancer columnBinderEnhancer) {
        this.columnBinderEnhancer = columnBinderEnhancer;
    }

    public Class<?> getDefaultDynamicEntityClass() {
        return defaultDynamicEntityClass;
    }

    public void setDefaultDynamicEntityClass(Class<?> defaultDynamicEntityClass) {
        this.defaultDynamicEntityClass = defaultDynamicEntityClass;
    }

    @Override
    public ICache<QueryPlanCacheKey, IOrmCachedQueryPlan> getQueryPlanCache() {
        return queryPlanCache;
    }

    public void setQueryPlanCache(ICache<QueryPlanCacheKey, IOrmCachedQueryPlan> queryPlanCache) {
        this.queryPlanCache = queryPlanCache;
    }

    public IJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setDynamicEntityNames(Set<String> dynamicEntityNames) {
        this.dynamicEntityNames = dynamicEntityNames;
    }

    @Override
    public IShardSelector getShardSelector() {
        return shardSelector;
    }

    public void setShardSelector(IShardSelector shardSelector) {
        this.shardSelector = shardSelector;
    }

    @Override
    public IDaoMetrics getDaoMetrics() {
        return jdbc().getDaoMetrics();
    }

    @Override
    public ICacheProvider getGlobalCache() {
        return globalCache;
    }

    public void setGlobalCache(ICacheProvider globalCache) {
        this.globalCache = globalCache;
    }

    public IQueryExecutor getDefaultQueryExecutor() {
        return defaultQueryExecutor;
    }

    public void setDefaultQueryExecutor(IQueryExecutor defaultQueryExecutor) {
        this.defaultQueryExecutor = defaultQueryExecutor;
    }

    public IBeanProvider getBeanProvider() {
        return beanProvider;
    }

    public void setBeanProvider(IBeanProvider beanProvider) {
        this.beanProvider = beanProvider;
    }

    public IClassLoader getEntityClassLoader() {
        return entityClassLoader;
    }

    public void setEntityClassLoader(IClassLoader entityClassLoader) {
        this.entityClassLoader = entityClassLoader;
    }

    public List<IOrmInterceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<IOrmInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Override
    public void addInterceptor(IOrmInterceptor interceptor) {
        List<IOrmInterceptor> list = new ArrayList<>(interceptors.size() + 1);
        list.addAll(interceptors);
        list.add(interceptor);
        this.interceptors = list;
    }

    @Override
    public void removeInterceptor(IOrmInterceptor interceptor) {
        List<IOrmInterceptor> list = new ArrayList<>(interceptors);
        list.remove(interceptor);
        this.interceptors = list;
    }

    @Override
    public long newDeleteVersion() {
        return CoreMetrics.currentTimeMillis();
    }

    public IOrmDaoListener getDaoListener() {
        return daoListeners;
    }

    @Override
    public void addDaoListener(IOrmDaoListener daoListener) {
        if (daoListeners == null)
            daoListeners = new MultiOrmDaoListener();

        daoListeners.addDaoListener(daoListener);
    }

    public void setDaoListeners(List<IOrmDaoListener> listeners) {
        if (listeners != null) {
            listeners.forEach(this::addDaoListener);
        }
    }

    @Override
    public void removeDaoListener(IOrmDaoListener daoListener) {
        if (daoListeners == null)
            return;
        daoListeners.removeDaoListener(daoListener);
    }

    public Map<String, IQueryExecutor> getQueryExecutors() {
        return queryExecutors;
    }

    public void setQueryExecutors(Map<String, IQueryExecutor> queryExecutors) {
        this.queryExecutors = queryExecutors;
    }

    public IDialectProvider getDialectProvider() {
        return dialectProvider;
    }

    public void setDialectProvider(IDialectProvider dialectProvider) {
        this.dialectProvider = dialectProvider;
    }

    public void clearQueryPlanCache() {
        this.getQueryPlanCache().clear();
    }

    @Override
    public void clearQueryCache() {
        jdbc().clearQueryCache();
    }

    @Override
    public void clearQueryCacheFor(String cacheName) {
        jdbc().clearQueryCacheFor(cacheName);
    }

    @Override
    public void evictQueryCache(String cacheName, Serializable cacheKey) {
        jdbc().evictQueryCache(cacheName, cacheKey);
    }

    @Override
    public IOrmSession openSession(boolean stateless) {
        return new OrmSessionImpl(stateless, this, getInterceptors());
    }

    @Override
    public IOrmModel getOrmModel() {
        return getLoadedOrmModel().getOrmModel();
    }

    @Override
    public ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete) {
        return compileSql(name, sqlText, disableLogicalDelete, defaultAstTransformer, true, false, false);
    }

    @Override
    public ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                                   boolean allowUnderscoreName, boolean enableFilter) {
        return compileSql(name, sqlText, disableLogicalDelete, defaultAstTransformer, true, allowUnderscoreName, enableFilter);
    }

    @Override
    public ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                                   IEqlAstTransformer astTransformer, boolean useCache,
                                   boolean allowUnderscoreName, boolean enableFilter) {
        return getLoadedOrmModel().compileSql(name, sqlText, disableLogicalDelete, astTransformer, useCache,
                allowUnderscoreName, enableFilter);
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        return getDialectProvider().getDialectForQuerySpace(querySpace);
    }

    @Override
    public IOrmMetrics getOrmMetrics() {
        return ormMetrics;
    }

    public void setOrmMetrics(IOrmMetrics ormMetrics) {
        this.ormMetrics = ormMetrics;
    }

    @Override
    public ICache<String, Object> getGlobalCache(String referenceName) {
        return getGlobalCache().getCache(referenceName);
    }

    @Override
    public IQueryExecutor getQueryExecutor(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);
        IQueryExecutor executor = getQueryExecutors().get(querySpace);
        if (executor == null)
            executor = getDefaultQueryExecutor();
        return executor;
    }

    @Override
    public IEntityPersistDriver createEntityPersistDriver(String driverName) {
        if (driverName == null || driverName.equals("jdbc"))
            return new JdbcEntityPersistDriver();
        return (IEntityPersistDriver) getBean("entityPersister_" + driverName, true);
    }

    @Override
    public ICollectionPersistDriver createCollectionPersistDriver(String driverName) {
        if (driverName == null || driverName.equals("jdbc"))
            return new JdbcCollectionPersistDriver();

        return (ICollectionPersistDriver) getBean("collectionPersister_" + driverName, true);
    }

    @Override
    public IOrmComponent newComponent(String componentName) {
        return (IOrmComponent) ReflectionManager.instance().loadClassModel(componentName).newInstance();
    }

    @Override
    public IBeanConstructor getEntityConstructor(IEntityModel entityModel) {
        IClassModel classModel = getEntityClassModel(entityModel);

        IBeanModel beanModel = classModel.getBeanModel();
        return () -> {
            IOrmEntity entity = (IOrmEntity) beanModel.newInstance();
            entity.orm_entityModel(entityModel);
            return entity;
        };
    }

    @Override
    public IClassModel getEntityClassModel(IEntityModel entityModel) {
        IClassModel classModel;
        if (dynamicEntityNames.contains(entityModel.getName())) {
            classModel = ReflectionManager.instance().getClassModel(getDefaultDynamicEntityClass());
        } else {
            String className = entityModel.getClassName();
            if (className == null)
                className = entityModel.getName();
            classModel = ReflectionManager.instance().loadClassModel(className);
        }
        return classModel;
    }

    @Override
    public ITransactionTemplate txn() {
        return jdbc().txn();
    }

    @Override
    public IJdbcTemplate jdbc() {
        return getJdbcTemplate();
    }

    @Override
    public long newSessionRevVer() {
        return jdbc().getDbEstimatedClock(null).getMaxCurrentTimeMillis();
    }

    @Override
    public void close() throws Exception {
        ormModelHolder.close();
    }

    public Object getBean(String name, boolean mustPrototype) {
        if (mustPrototype && !beanProvider.isPrototypeScope(name))
            throw new OrmException(ERR_ORM_BEAN_NOT_PROTOTYPE_SCOPE).param(ARG_BEAN_NAME, name);

        LOG.debug("orm.bean_provider_get_bean:name={}", name);
        return beanProvider.getBean(name);
    }

    @Override
    public ILoadedOrmModel reloadModel() {
        ormModelHolder.clearCache();
        return ormModelHolder.getOrmModel(this);
    }
}
