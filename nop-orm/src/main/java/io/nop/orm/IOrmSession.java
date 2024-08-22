/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.cache.ICache;
import io.nop.core.lang.sql.SQL;
import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataSet;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public interface IOrmSession extends AutoCloseable {

    ILoadedOrmModel getLoadedOrmModel();

    void close();

    /**
     * 是否无状态的session。无状态session不在内存中缓存从数据库中装载得到的实体，它总是立刻操纵数据库。
     *
     * @return
     */
    boolean isStateless();

    /**
     * 只读session不允许执行修改、删除以及flush等操作
     *
     * @return
     */
    boolean isReadonly();

    void setReadonly(boolean readonly);

    boolean isDirty();

    /**
     * 执行数据库操作，将内存中记录的修改同步到数据库中
     */
    void flush();

    boolean isClosed();

    /**
     * 在session范围内有效的缓存。
     */
    ICache<Object, Object> getSessionCache();

    /**
     * 通过批量记载队列可以避免列表页面上每个实体都加载字典表导致多次数据库访问的问题。
     *
     * @return
     */
    IOrmBatchLoadQueue getBatchLoadQueue();

    default void forceLoad(IOrmEntity entity, FieldSelectionBean selection) {
        IOrmBatchLoadQueue queue = getBatchLoadQueue();
        queue.enqueueEntity(entity, selection);
        queue.flush();
    }

    void flushBatchLoadQueue();

    /**
     * 判断实体是否属于本session
     *
     * @param object
     * @return
     */
    boolean contains(IOrmEntity object);

    /**
     * 将实体从session中移除，实体状态转变为detached, 不再能够延迟加载，也不再参与flush处理
     *
     * @param entity
     */
    void evict(IOrmEntity entity);

    /**
     * 移除session中指定类型的所有实体
     *
     * @param entityName
     */
    void evictAll(String entityName);

    /**
     * 清空session
     */
    void clear();

    /**
     * 将所有新增记录删除，所有修改恢复到修改前，所有删除取消
     */
    void reset();

    IOrmEntity newEntity(String entityName);

    /**
     * 如果实体已经被标记为删除或者从数据库中未找到，则返回null
     *
     * @param entityName
     * @param id
     * @return
     */
    IOrmEntity get(String entityName, Object id);

    /**
     * 如果session中已存在，直接返回实体，否则创建一个proxy返回。此函数不会访问数据库，返回值也不可能为null
     *
     * @param entityName
     * @param id
     * @return
     */
    IOrmEntity load(String entityName, Object id);

    /**
     * 清除实体上的dirty标记，将实体状态转回proxy状态，这样当下次访问实体属性时会重新从数据库装载
     *
     * @param entity
     */
    void unload(IOrmEntity entity);

    void unloadCollection(Collection<? extends IOrmEntity> coll);

    /**
     * 将新建的实体与session关联
     *
     * @param entity
     * @return 返回实体id
     */
    Object save(IOrmEntity entity);

    Object saveDirectly(IOrmEntity entity);

    void updateDirectly(IOrmEntity entity);

    void deleteDirectly(IOrmEntity entity);

    /**
     * 重新从数据库中加载数据，放弃当前对象上的修改
     *
     * @param entity
     */
    void refresh(IOrmEntity entity);

    /**
     * 不保存，只是直接初始化实体id
     *
     * @param entity
     * @return
     */
    Object initEntityId(IOrmEntity entity);

    /**
     * 调用select for update来锁定实体。如果实体当前处于dirty状态，则会抛出异常，lock语义应该非常严格，表示当前实体状态必须与数据库一致。
     *
     * @param entity
     */
    void lock(IOrmEntity entity);

    /**
     * 保存或者更新实体
     *
     * @param entity
     */
    void saveOrUpdate(IOrmEntity entity);

    /**
     * 删除实体
     *
     * @param entity
     */
    void delete(IOrmEntity entity);

    /**
     * 将session外的实体与session绑定
     *
     * @param entity
     */
    void attach(IOrmEntity entity);

    void detach(IOrmEntity entity, FieldSelectionBean selection);

    <T extends IOrmEntity> T findFirstByExample(T example);

    <T extends IOrmEntity> List<T> findAllByExample(T example, List<OrderFieldBean> orderBy);

    <T extends IOrmEntity> List<T> findPageByExample(T example, List<OrderFieldBean> orderBy, long offset, int limit);

    long countByExample(IOrmEntity example);

    long deleteByExample(IOrmEntity example);

    long updateByExample(IOrmEntity example, IOrmEntity updated);

    /**
     * 假定所有数据都在内存中，直接从session中查找集合元素，并将其组装为集合对象
     *
     * @param collectionName
     */
    void assembleAllCollectionInMemory(String collectionName);

    void assembleCollectionInMemory(Collection<? extends IOrmEntity> coll);

    void assembleSelectionInMemory(Object ormObject, FieldSelectionBean selection);

    long executeUpdate(SQL sql);

    <T> T executeQuery(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<? super IDataSet, T> callback);

    <T> T executeStatement(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<IComplexDataSet, T> callback,
                           ICancelToken cancelToken);

    /**
     * 实体模式下所有的update和delete语句都转换为先装载实体，然后再对单实体进行操作
     */
    boolean isEntityMode();

    void setEntityMode(boolean entityMode);
}