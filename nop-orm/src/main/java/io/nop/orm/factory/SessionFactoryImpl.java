/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.factory;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.IoHelper;
import io.nop.core.model.graph.TopoEntry;
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
import io.nop.orm.IOrmComponent;
import io.nop.orm.IOrmDaoListener;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.IOrmSession;
import io.nop.orm.QueryPlanCacheKey;
import io.nop.orm.compile.EqlCompileContext;
import io.nop.orm.driver.ICollectionPersistDriver;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.driver.jdbc.JdbcCollectionPersistDriver;
import io.nop.orm.driver.jdbc.JdbcEntityPersistDriver;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.binder.IOrmColumnBinderEnhancer;
import io.nop.orm.eql.compile.EqlCompiler;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.SqlExprMetaCache;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.impl.MultiOrmDaoListener;
import io.nop.orm.loader.IQueryExecutor;
import io.nop.orm.metrics.IOrmMetrics;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.init.OrmModelUpdater;
import io.nop.orm.persister.ICollectionPersister;
import io.nop.orm.persister.IEntityPersister;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.session.OrmSessionImpl;
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
import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_BEAN_NOT_PROTOTYPE_SCOPE;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_COLLECTION_PERSISTER;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_ENTITY_PERSISTER;

/**
 * @author canonical_entropy@163.com
 */
public class SessionFactoryImpl implements IPersistEnv {
    static final Logger LOG = LoggerFactory.getLogger(SessionFactoryImpl.class);

    private IJdbcTemplate jdbcTemplate;

    private IShardSelector shardSelector = EmptyShardSelector.INSTANCE;

    private ICacheProvider globalCache;

    private ICache<QueryPlanCacheKey, ICompiledSql> queryPlanCache;

    private IQueryExecutor defaultQueryExecutor;

    private IBeanProvider beanProvider;

    private IClassLoader entityClassLoader;

    private List<IOrmInterceptor> interceptors = Collections.emptyList();

    private MultiOrmDaoListener daoListeners = null;

    private Map<String, IQueryExecutor> queryExecutors = Collections.emptyMap();

    private IDialectProvider dialectProvider;

    private Map<String, IEntityPersister> entityPersisters = Collections.emptyMap();
    private Map<String, ICollectionPersister> collectionPersisters = Collections.emptyMap();
    private IOrmModel ormModel;
    private IOrmMetrics ormMetrics;

    private Class<?> defaultDynamicEntityClass = DynamicOrmEntity.class;
    private Set<String> dynamicEntityNames = Collections.emptySet();

    private IOrmColumnBinderEnhancer columnBinderEnhancer;

    private SqlExprMetaCache sqlExprMetaCache;

    private IEqlAstTransformer eqlAstTransformer;

    private ISequenceGenerator sequenceGenerator;

    private Runnable reloadFunction;

    public Runnable getReloadFunction() {
        return reloadFunction;
    }

    public void setReloadFunction(Runnable reloadFunction) {
        this.reloadFunction = reloadFunction;
    }

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

    public void setEqlAstTransformer(IEqlAstTransformer eqlAstTransformer) {
        this.eqlAstTransformer = eqlAstTransformer;
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
    public ICache<QueryPlanCacheKey, ICompiledSql> getQueryPlanCache() {
        return queryPlanCache;
    }

    public void setQueryPlanCache(ICache<QueryPlanCacheKey, ICompiledSql> queryPlanCache) {
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

    public void setEntityPersisters(Map<String, IEntityPersister> entityPersisters) {
        this.entityPersisters = entityPersisters;
    }

    public void setCollectionPersisters(Map<String, ICollectionPersister> collectionPersisters) {
        this.collectionPersisters = collectionPersisters;
    }

    public SqlExprMetaCache getSqlExprMetaCache() {
        return sqlExprMetaCache;
    }

    public void setSqlExprMetaCache(SqlExprMetaCache sqlExprMetaCache) {
        this.sqlExprMetaCache = sqlExprMetaCache;
    }

    public EntityTableMeta resolveEntityTableMeta(String entityName) {
        return sqlExprMetaCache.getEntityTableMeta(entityName);
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
        return ormModel;
    }

    public void setOrmModel(IOrmModel ormModel) {
        this.ormModel = ormModel;
    }

    @Override
    public <T> T getExtension(String entityName, Class<T> extensionClass) {
        return requireEntityPersister(entityName).getExtension(extensionClass);
    }

    @Override
    public ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete) {
        return compileSql(name, sqlText, disableLogicalDelete, eqlAstTransformer, true);
    }

    @Override
    public ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                                   IEqlAstTransformer astTransformer, boolean useCache) {

        if (useCache) {
            QueryPlanCacheKey key = new QueryPlanCacheKey(name, sqlText, disableLogicalDelete);
            ICompiledSql result = getQueryPlanCache().get(key);
            if (result == null) {
                ISqlCompileContext ctx = new EqlCompileContext(this, disableLogicalDelete, astTransformer);
                result = new EqlCompiler().compile(name, sqlText, ctx);
                getQueryPlanCache().put(key, result);
            }
            return result;
        } else {
            ISqlCompileContext ctx = new EqlCompileContext(this, disableLogicalDelete, astTransformer);
            return new EqlCompiler().compile(name, sqlText, ctx);
        }
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
    public IEntityPersister requireEntityPersister(String entityName) {
        IEntityPersister persister = entityPersisters.get(entityName);
        if (persister == null)
            throw new OrmException(ERR_ORM_UNKNOWN_ENTITY_PERSISTER).param(ARG_ENTITY_NAME, entityName);
        return persister;
    }

    @Override
    public ICollectionPersister requireCollectionPersister(String collectionName) {
        ICollectionPersister persister = collectionPersisters.get(collectionName);
        if (persister == null)
            throw new OrmException(ERR_ORM_UNKNOWN_COLLECTION_PERSISTER).param(ARG_COLLECTION_NAME, collectionName);
        return persister;
    }

    @Override
    public TopoEntry<? extends IEntityModel> getEntityModelTopoEntry(String entityName) {
        return ormModel.getTopoEntry(entityName);
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
        for (IEntityPersister persister : this.entityPersisters.values()) {
            IoHelper.safeCloseObject(persister);
        }

        for (ICollectionPersister persister : this.collectionPersisters.values()) {
            IoHelper.safeCloseObject(persister);
        }

        this.entityPersisters.clear();
        this.collectionPersisters.clear();
    }

    public Object getBean(String name, boolean mustPrototype) {
        if (mustPrototype && !beanProvider.isPrototypeScope(name))
            throw new OrmException(ERR_ORM_BEAN_NOT_PROTOTYPE_SCOPE).param(ARG_BEAN_NAME, name);

        LOG.debug("orm.bean_provider_get_bean:name={}", name);
        return beanProvider.getBean(name);
    }

    @Override
    public void reloadModel() {
        if (this.reloadFunction != null)
            this.reloadFunction.run();
    }

    @Override
    public void updateDynamicModel(Set<String> moduleNames, IOrmModel dynModel) {
        // 更新所有标记为dynamic的模型。这里需要强制要求非dynamic的模型不会引用dynModel中的实体
        this.ormModel = new OrmModelUpdater(ormModel).updateDynamicModel(moduleNames, dynModel);
    }
}
