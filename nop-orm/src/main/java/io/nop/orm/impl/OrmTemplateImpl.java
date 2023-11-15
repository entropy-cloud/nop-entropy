/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.impl;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.AbstractSqlExecutor;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.rowmapper.SmartRowMapper;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.mdx.MdxQueryExecutor;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.support.OrmEntityHelper;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.nop.orm.OrmErrors.ERR_ORM_NOT_IN_SESSION;

public class OrmTemplateImpl extends AbstractSqlExecutor implements IOrmTemplate {
    static final Logger LOG = LoggerFactory.getLogger(OrmTemplateImpl.class);

    private IOrmSessionFactory sessionFactory;
    private boolean stateless;

    public OrmTemplateImpl(IOrmSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public OrmTemplateImpl() {
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        return sessionFactory.getDialectForQuerySpace(querySpace);
    }

    public IOrmSessionFactory getSessionFactory() {
        return sessionFactory;
    }

    @Inject
    public void setSessionFactory(IOrmSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public IOrmModel getOrmModel() {
        return sessionFactory.getOrmModel();
    }

    @Override
    public ICacheProvider getCacheProvider() {
        return sessionFactory.getGlobalCache();
    }

    @Override
    public <T> T getExtension(String entityName, Class<T> extensionClass) {
        return sessionFactory.getExtension(entityName, extensionClass);
    }

    @Override
    public void clearQueryCache() {
        sessionFactory.clearQueryCache();
    }

    @Override
    public void clearQueryCacheFor(String cacheName) {
        sessionFactory.clearQueryCacheFor(cacheName);
    }

    @Override
    public void evictQueryCache(String cacheName, Serializable cacheKey) {
        sessionFactory.evictQueryCache(cacheName, cacheKey);
    }

    @Override
    public void clearQueryPlanCache() {
        sessionFactory.getQueryPlanCache().clear();
    }

    @Override
    public void clearGlobalCache() {
        sessionFactory.getGlobalCache().clearAllCache();
    }

    @Override
    public void clearGlobalCacheFor(String referenceName) {
        sessionFactory.getGlobalCache().clearCache(referenceName);
    }

    @Override
    public <T> IRowMapper<T> getDefaultRowMapper() {
        return (IRowMapper<T>) SmartRowMapper.CASE_SENSITIVE;
    }

    @Override
    public long executeUpdate(SQL sql) {
        return runInSession(session -> session.executeUpdate(sql));
    }

    @Override
    public <T> T executeQuery(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<? super IDataSet, T> callback) {
        return runInSession(session -> session.executeQuery(sql, range, callback));
    }

    @Override
    public <T> T executeStatement(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<IComplexDataSet, T> callback,
                                  ICancelToken cancelToken) {
        return runInSession(session -> session.executeStatement(sql, range, callback, cancelToken));
    }

    @Override
    public Object castId(String entityName, Object id) {
        return OrmEntityHelper.castId(sessionFactory.getOrmModel().requireEntityModel(entityName), id);
    }

    @Override
    public List<Object> castIds(String entityName, Collection<?> ids) {
        IEntityModel entityModel = sessionFactory.getOrmModel().requireEntityModel(entityName);
        return ids.stream().map(value -> OrmEntityHelper.castId(entityModel, value)).collect(Collectors.toList());
    }

    @Override
    public IOrmTemplate forStateless() {
        if (this.stateless)
            return this;

        OrmTemplateImpl orm = new OrmTemplateImpl();
        orm.stateless = true;
        orm.setSessionFactory(sessionFactory);
        return orm;
    }

    @Override
    public IOrmSession currentSession() {
        return OrmSessionRegistry.instance().get(sessionFactory);
    }

    @Nonnull
    @Override
    public IOrmSession requireSession() {
        IOrmSession session = currentSession();
        if (session == null)
            throw new OrmException(ERR_ORM_NOT_IN_SESSION);
        return session;
    }

    @Override
    public void flushSession() {
        IOrmSession session = currentSession();
        if (session != null)
            session.flush();
    }

    @Override
    public void clearSession() {
        IOrmSession session = currentSession();
        if (session != null) {
            session.clear();
        }
    }

    @Override
    public <T> T runInSession(Function<IOrmSession, T> callback) {
        IOrmSession session = OrmSessionRegistry.instance().get(sessionFactory);
        if (session != null && session.isStateless() == stateless) {
            return callback.apply(session);
        }

        return runInNewSession(callback);
    }

    @Override
    public <T> T runInNewSession(Function<IOrmSession, T> callback) {
        IOrmSession session = sessionFactory.openSession(stateless);
        OrmSessionRegistry registry = OrmSessionRegistry.instance();
        IOrmSession oldSession = registry.put(sessionFactory, session);
        try {
            T ret = callback.apply(session);
            session.flush();
            return ret;
        } finally {
            IOrmSession s = registry.put(sessionFactory, oldSession);
            // 如果已经unregister，则s为null，这里将不会关闭session
            if (s == session) {
                session.close();
            } else {
                LOG.info("nop.orm.skip-session-close-when-not-current-session");
            }
        }
    }

    @Override
    public <T> CompletionStage<T> runInSessionAsync(Function<IOrmSession, CompletionStage<T>> callback) {
        IOrmSession session = OrmSessionRegistry.instance().get(sessionFactory);
        if (session != null && session.isStateless() == stateless) {
            return ContextProvider.thenOnContext(callback.apply(session));
        }

        return runInNewSessionAsync(callback);
    }

    @Override
    public <T> CompletionStage<T> runInNewSessionAsync(Function<IOrmSession, CompletionStage<T>> callback) {
        IOrmSession session = sessionFactory.openSession(stateless);
        OrmSessionRegistry registry = OrmSessionRegistry.instance();
        IOrmSession oldSession = registry.put(sessionFactory, session);

        CompletionStage<T> future;
        try {
            future = callback.apply(session);
        } catch (Throwable t) {
            future = FutureHelper.reject(t);
        }
        return ContextProvider.thenOnContext(future).whenComplete((ret, err) -> {
            try {
                if (err != null) {
                    session.flush();
                }
            } finally {
                IOrmSession s = registry.put(sessionFactory, oldSession);
                // 如果已经unregister，则s为null，这里将不会关闭session
                if (s == session) {
                    session.close();
                } else {
                    LOG.info("nop.orm.skip-session-close-when-not-current-session");
                }
            }
        });
    }

    @Override
    public IOrmSession unregisterSession() {
        return OrmSessionRegistry.instance().remove(sessionFactory);
    }

    @Override
    public IOrmSession registerSession(IOrmSession session) {
        return OrmSessionRegistry.instance().put(sessionFactory, session);
    }

    @Override
    public void runInSession(Runnable callback) {
        runInSession(session -> {
            callback.run();
            return null;
        });
    }

    @Override
    public IOrmEntity get(String entityName, Object id) {
        return runInSession(session -> session.get(entityName, id));
    }

    @Override
    public IOrmEntity load(String entityName, Object id) {
        return runInSession(session -> session.load(entityName, id));
    }

    @Override
    public Object save(IOrmEntity entity) {
        return runInSession(session -> session.save(entity));
    }

    @Override
    public Object saveDirectly(IOrmEntity entity) {
        return runInSession(session -> session.saveDirectly(entity));
    }

    @Override
    public void updateDirectly(IOrmEntity entity) {
        runInSession(session -> {
            session.updateDirectly(entity);
            return null;
        });
    }

    @Override
    public void deleteDirectly(IOrmEntity entity) {
        runInSession(session -> {
            session.deleteDirectly(entity);
            return null;
        });
    }

    @Override
    public void saveOrUpdate(IOrmEntity entity) {
        runInSession(session -> {
            session.saveOrUpdate(entity);
            return null;
        });
    }

    @Override
    public void batchSaveOrUpdate(Collection<? extends IOrmEntity> list) {
        if (list == null || list.isEmpty())
            return;

        runInSession(session -> {
            for (IOrmEntity entity : list) {
                session.saveOrUpdate(entity);
            }
            return null;
        });
    }

    @Override
    public void batchDelete(Collection<? extends IOrmEntity> list) {
        if (list == null || list.isEmpty())
            return;

        runInSession(session -> {
            for (IOrmEntity entity : list) {
                session.delete(entity);
            }
            return null;
        });
    }

    @Override
    public void delete(IOrmEntity entity) {
        runInSession(session -> {
            session.delete(entity);
            return null;
        });
    }

    @Override
    public boolean contains(IOrmEntity entity) {
        return runInSession(session -> session.contains(entity));
    }

    @Override
    public void evict(IOrmEntity entity) {
        runInSession(session -> {
            session.evict(entity);
            return null;
        });
    }

    @Override
    public void refresh(IOrmEntity entity) {
        runInSession(session -> {
            session.refresh(entity);
            return null;
        });
    }

    @Override
    public void lock(IOrmEntity entity) {
        runInSession(session -> {
            session.lock(entity);
            return null;
        });
    }

    @Override
    public void evictAll(String entityName) {
        runInSession(session -> {
            session.evictAll(entityName);
            return null;
        });
    }

    @Override
    public Object initEntityId(IOrmEntity entity) {
        return runInSession(session -> session.initEntityId(entity));
    }

    @Override
    public <T> List<T> findListByQuery(QueryBean query, IRowMapper<T> rowMapper) {
        return new MdxQueryExecutor(this).findList(query, rowMapper);
    }

    @Override
    public <T> T findFirstByQuery(QueryBean query, IRowMapper<T> rowMapper) {
        return new MdxQueryExecutor(this).findFirst(query, rowMapper);
    }

    @Override
    public boolean existsByQuery(QueryBean query) {
        return new MdxQueryExecutor(this).exists(query);
    }

    @Override
    public boolean isValidEntityName(String entityName) {
        return sessionFactory.getOrmModel().getEntityModel(entityName) != null;
    }

    @Override
    public String getFullEntityName(String entityName) {
        IEntityModel entityModel = sessionFactory.getOrmModel().requireEntityModel(entityName);
        return entityModel.getName();
    }

    @Override
    public IOrmEntity newEntity(String entityName) {
        return runInSession(session -> session.newEntity(entityName));
    }

    @Override
    public void attach(IOrmEntity entity) {
        requireSession().attach(entity);
    }

    @Override
    public void detach(IOrmEntity entity, FieldSelectionBean selection) {
        requireSession().detach(entity, selection);
    }

    @Override
    public void assembleAllCollectionInMemory(String collectionName) {
        requireSession().assembleAllCollectionInMemory(collectionName);
    }

    @Override
    public void assembleCollectionInMemory(Collection<? extends IOrmEntity> coll) {
        requireSession().assembleCollectionInMemory(coll);
    }

    @Override
    public void assembleSelectionInMemory(Object ormObject, FieldSelectionBean selection) {
        requireSession().assembleSelectionInMemory(ormObject, selection);
    }

    @Override
    public void batchLoadProps(Collection<?> entities, Collection<String> propNames) {
        if (entities == null || propNames == null)
            return;
        IOrmSession session = requireSession();
        IOrmBatchLoadQueue queue = session.getBatchLoadQueue();
        queue.enqueueManyProps(entities, propNames);
        queue.flush();
    }

    @Override
    public void batchLoadSelection(Collection<?> entities, FieldSelectionBean selection) {
        if (entities == null || selection == null)
            return;
        IOrmSession session = requireSession();
        IOrmBatchLoadQueue queue = session.getBatchLoadQueue();
        queue.enqueueSelection(entities, selection);
        queue.flush();
    }

    @Override
    public void forceLoad(IOrmEntity entity, FieldSelectionBean selection) {
        requireSession().forceLoad(entity, selection);
    }

    @Override
    public ICache<Object, Object> sessionCache() {
        IOrmSession session = currentSession();
        return session == null ? null : session.getSessionCache();
    }

    @Override
    public <T> T cacheGet(Object key, Supplier<T> loader) {
        return (T) requireSession().getSessionCache().computeIfAbsent(key, k -> loader.get());
    }

    @Override
    public <T> T cacheGet(Object key) {
        return (T) requireSession().getSessionCache().get(key);
    }

    @Override
    public void cacheRemove(Object key) {
        requireSession().getSessionCache().remove(key);
    }

    @Override
    public void cachePut(Object key, Object value) {
        requireSession().getSessionCache().put(key, value);
    }

    @Override
    public IEstimatedClock getDbEstimatedClock(String querySpace) {
        return sessionFactory.getJdbcTemplate().getDbEstimatedClock(querySpace);
    }

    @Override
    public void reloadModel() {
        sessionFactory.reloadModel();
    }
}
