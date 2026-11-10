/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
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
import io.nop.orm.OrmConstants;
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
        IOrmSession session = currentSession();
        if (session != null)
            return session.getLoadedOrmModel().getOrmModel();
        return sessionFactory.getOrmModel();
    }

    @Override
    public ICacheProvider getCacheProvider() {
        return sessionFactory.getGlobalCache();
    }

    @Override
    public <T> T getExtension(String entityName, Class<T> extensionClass) {
        IOrmSession session = currentSession();
        if (session != null)
            return session.getLoadedOrmModel().getExtension(entityName, extensionClass);

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
            restoreAndCloseSession(registry, session, oldSession);
        }
    }

    /**
     * plan 2517 R2-14 裁定①（C2-003 吸收子项 b）：单槽 put/restore 交错时不再跳过关闭——
     * close 幂等兜底，无条件关闭本次会话，消除「跳过-关闭」分支的会话泄漏半边。
     * put 返回值 != 本次会话时升级 WARN 可观测（nop.orm.skip-session-close-when-not-current-session）。
     */
    private void restoreAndCloseSession(OrmSessionRegistry registry, IOrmSession session, IOrmSession oldSession) {
        IOrmSession s = registry.put(sessionFactory, oldSession);
        if (s != session) {
            LOG.warn("nop.orm.skip-session-close-when-not-current-session");
        }
        session.close();
    }

    /**
     * plan 2517 R2-14 裁定⑤（C2-002）：完成线程只投递不执行——收尾任务经 context.execute 回 context 队列
     * （对齐 thenOnContext 出口泵语义）。降级路径：context 为 null 时内联执行（无 context 纪律可依赖，由裁定④
     * session 互斥兜底）；closed context 上 execute 抛出的异常不吞会话——裁定⑪兜底登记负责关闭。
     */
    /**
     * 2.3-C4-002（plan 2553 裁定 J，R1-Major-4 作用域限定）：flush 载体隔离可装配点——
     * 注入 flushExecutor 后 finalize 任务（含 session.flush 的 JDBC I/O）投递至注入执行器；
     * 缺省 null = 调用线程现行语义（零破坏）。作用域显式限定 runInNewSessionAsync finalize 路径
     * ——用户直调 session.flush() 不在 seam 内（session/factory 无 OrmTemplate 依赖；全量接管
     * 需 IPersistEnv 贯穿=watch-only，plan 2553 Deferred）。
     */
    private volatile java.util.concurrent.Executor flushExecutor;

    public void setFlushExecutor(java.util.concurrent.Executor flushExecutor) {
        this.flushExecutor = flushExecutor;
    }

    private void dispatchSessionFinalize(IContext context, Runnable task) {
        java.util.concurrent.Executor executor = this.flushExecutor;
        if (executor != null) {
            // 载体隔离档：finalize 整体（含 flush I/O）投递注入执行器
            try {
                executor.execute(task);
            } catch (Throwable t) {
                LOG.error("nop.orm.session-finalize-dispatch-fail", t);
            }
            return;
        }
        if (context == null) {
            task.run();
            return;
        }
        try {
            context.execute(task);
        } catch (Throwable t) {
            LOG.error("nop.orm.session-finalize-dispatch-fail", t);
        }
    }

    @Override
    public <T> CompletionStage<T> runInSessionAsync(Function<IOrmSession, CompletionStage<T>> callback) {
        IOrmSession session = OrmSessionRegistry.instance().get(sessionFactory);
        if (session != null && session.isStateless() == stateless) {
            // 2.3-C3-002（plan 2553 裁定 F）：异常契约与新会话分支对齐——同步抛出收敛为
            // failed future（原裸调用会穿透给调用方，两分支行为不一致）
            try {
                return ContextProvider.thenOnContext(callback.apply(session));
            } catch (Throwable t) {
                return FutureHelper.reject(t);
            }
        }

        return runInNewSessionAsync(callback);
    }

    @Override
    public <T> CompletionStage<T> runInNewSessionAsync(Function<IOrmSession, CompletionStage<T>> callback) {
        IOrmSession session = sessionFactory.openSession(stateless);
        OrmSessionRegistry registry = OrmSessionRegistry.instance();
        IContext context = ContextProvider.currentContext();
        IOrmSession oldSession = registry.put(sessionFactory, session);

        // plan 2517 R2-14 裁定⑪（C3-001）：注册新会话时挂 context 生命周期兜底 close 登记，
        // 上游 future 永不完成时由 sweepClosedContexts 在 context 关闭后兜底关闭，消除注册表滞留泄漏
        OrmSessionRegistry.registerPendingClose(context, session);

        CompletionStage<T> future;
        try {
            future = callback.apply(session);
        } catch (Throwable t) {
            future = FutureHelper.reject(t);
        }
        return ContextProvider.thenOnContext(future).whenComplete((ret, err) -> {
            // plan 2517 R2-14 裁定⑤（C2-002）：完成线程只投递不执行，收尾（flush/registry 恢复/close）
            // 整段经 context.execute 回 context 队列执行
            dispatchSessionFinalize(context, () -> {
                try {
                    if (err == null) {
                        session.flush();
                    }
                } finally {
                    restoreAndCloseSession(registry, session, oldSession);
                    OrmSessionRegistry.unregisterPendingClose(session);
                }
            });
        });
    }

    @Override
    public IOrmSession unregisterSession() {
        // plan 2517 R2-14 裁定①：公共 unregister 升两参身份一致移除（remove(key, value) 语义），
        // 防止并发替换场景误删他方会话
        OrmSessionRegistry registry = OrmSessionRegistry.instance();
        IOrmSession session = registry.get(sessionFactory);
        if (session == null)
            return null;
        registry.remove(sessionFactory, session);
        return session;
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
    public void updateManyDirectly(Collection<? extends IOrmEntity> entities) {
        runInSession(session -> {
            for (IOrmEntity entity : entities) {
                session.updateDirectly(entity, false);
            }
            session.flushActionQueue();
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
    public boolean tryDelete(IOrmEntity entity) {
        return runInSession(session -> {
            SQL sql = buildDeleteEntitySql(entity);
            return session.executeUpdate(sql) > 0;
        });
    }

    SQL buildDeleteEntitySql(IOrmEntity entity) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.deleteFrom().sql(entity.orm_entityName())
                .where().eq(OrmConstants.PROP_ID, entity.orm_id());
        IEntityModel entityModel = entity.orm_entityModel();
        if (entityModel.getVersionPropModel() != null)
            sb.and().eq(entityModel.getVersionPropModel().getName(), entity.orm_propValue(entityModel.getVersionPropId()));
        return sb.end();
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
        IEntityModel entityModel = getOrmModel().requireEntityModel(entityName);
        return entityModel.getName();
    }

    @Override
    public IOrmEntity newEntity(String entityName) {
        return runInSession(session -> session.newEntity(entityName));
    }

    @Override
    public void attach(IOrmEntity entity, boolean cascade) {
        requireSession().attach(entity, cascade);
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
