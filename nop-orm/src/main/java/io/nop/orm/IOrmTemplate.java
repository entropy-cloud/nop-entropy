/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.cache.ICache;
import io.nop.dao.api.ISqlExecutor;
import io.nop.dataset.IRowMapper;
import io.nop.orm.model.IOrmModel;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

public interface IOrmTemplate extends ISqlExecutor {

    IOrmSessionFactory getSessionFactory();

    IOrmModel getOrmModel();

    <T> T getExtension(String entityName, Class<T> extensionClass);

    /**
     * 不使用一级缓存，使用无状态Session
     *
     * @return
     */
    IOrmTemplate forStateless();

    /**
     * 返回当前上下文环境中注册的session, 可能是null
     *
     * @return
     */
    IOrmSession currentSession();

    @Nonnull
    IOrmSession requireSession();

    /**
     * 相当于currentSession().flush()
     */
    void flushSession();

    /**
     * 清空一级缓存
     */
    void clearSession();

    /**
     * 在session环境中执行，可能使用上下文环境中注册的session, 也可能打开新的session
     *
     * @param <T>
     * @param callback
     * @return
     */
    <T> T runInSession(Function<IOrmSession, T> callback);

    <T> T runInNewSession(Function<IOrmSession, T> callback);

    <T> CompletionStage<T> runInSessionAsync(Function<IOrmSession, CompletionStage<T>> callback);

    <T> CompletionStage<T> runInNewSessionAsync(Function<IOrmSession, CompletionStage<T>> callback);

    @Internal
    IOrmSession unregisterSession();

    @Internal
    IOrmSession registerSession(IOrmSession session);

    /**
     * 在sesison环境中执行，可能使用上下文环境中注册的session, 也可能打开新的session
     *
     * @param callback
     * @return
     */
    void runInSession(Runnable callback);

    Object castId(String entityName, Object id);

    List<Object> castIds(String entityName, Collection<?> ids);

    /**
     * 根据主键查找实体，如果未找到则返回null。
     *
     * @param entityName
     * @param id
     * @return
     */
    IOrmEntity get(String entityName, Object id);

    /**
     * 如果session中已经存在对应记录，则直接返回，否则创建一个Proxy代理对象，放入session后返回该代理对象。
     *
     * @param entityName
     * @param id
     * @return
     */
    IOrmEntity load(String entityName, Object id);

    /**
     * 保存实体, 返回实体主键
     *
     * @param entity
     * @return
     */
    Object save(IOrmEntity entity);

    /**
     * 如果session已存在，则更新，否则新建
     *
     * @param entity
     */
    void saveOrUpdate(IOrmEntity entity);

    void batchSaveOrUpdate(Collection<? extends IOrmEntity> list);

    void batchDelete(Collection<? extends IOrmEntity> list);

    /**
     * 删除实体
     *
     * @param entity
     */
    void delete(IOrmEntity entity);

    /**
     * 判断session中是否已缓存实体
     *
     * @param entity
     * @return
     */
    boolean contains(IOrmEntity entity);

    /**
     * 从session中移除实体
     *
     * @param entity
     */
    void evict(IOrmEntity entity);

    /**
     * 重新从数据库中查询实体数据，更新实体属性。
     *
     * @param entity
     */
    void refresh(IOrmEntity entity);

    /**
     * 调用数据库的锁机制锁定一条记录
     *
     * @param entity
     */
    void lock(IOrmEntity entity);

    /**
     * 清空session中指定实体类的所有记录
     *
     * @param entityName
     */
    void evictAll(String entityName);

    Object initEntityId(IOrmEntity entity);

    <T> List<T> findListByQuery(QueryBean query, IRowMapper<T> rowMapper);

    <T> T findFirstByQuery(QueryBean query, IRowMapper<T> rowMapper);

    boolean existsByQuery(QueryBean query);

    /**
     * 判断实体类是否已经被定义
     *
     * @param entityName
     * @return
     */
    boolean isValidEntityName(String entityName);

    /**
     * 根据实体类的简写名称得到其全名
     *
     * @param entityName
     * @return
     */
    String getFullEntityName(String entityName);

    /**
     * 创建指定类型的实体对象
     *
     * @param entityName
     * @return
     */
    IOrmEntity newEntity(String entityName);

    void attach(IOrmEntity entity);

    void detach(IOrmEntity entity, FieldSelectionBean selection);

    void assembleAllCollectionInMemory(String collectionName);

    void assembleCollectionInMemory(Collection<? extends IOrmEntity> coll);

    /**
     * 假设所有关联对象已经全部加载到内存中。直接对内存中的数据进行过滤来初始化延迟加载的proxy对象或者集合
     *
     * @param ormObject 实体对象或者实体对象集合
     * @param selection 需要在内存中进行装配的关联属性
     */
    void assembleSelectionInMemory(Object ormObject, FieldSelectionBean selection);

    void batchLoadProps(Collection<?> objects, Collection<String> propNames);

    void batchLoadSelection(Collection<?> objects, FieldSelectionBean fields);

    void forceLoad(IOrmEntity entity, FieldSelectionBean selection);

    /**
     * session级别的自定义缓存，其中可以存放任意应用数据，当session关闭时缓存会被自动清空。
     */
    ICache<Object, Object> sessionCache();

    /**
     * 使用sessionCache缓存结果
     */
    <T> T cacheGet(Object key, Supplier<T> loader);

    /**
     * 从sessionCache中获取
     */
    <T> T cacheGet(Object key);

    void cacheRemove(Object key);

    void cachePut(Object key, Object value);

    /**
     * 清空查询计划缓存。所有租户共享查询计划缓存
     */
    void clearQueryPlanCache();

    /**
     * 清空所有实体数据的二级缓存
     */
    void clearGlobalCache();

    /**
     * 清空涉及到指定实体类型的二级数据缓存
     *
     * @param referenceName 对应entityName或collectionRole
     */
    void clearGlobalCacheFor(String referenceName);
}