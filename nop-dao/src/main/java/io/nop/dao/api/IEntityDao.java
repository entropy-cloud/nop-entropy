/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.api;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.collections.iterator.FindNextPageIterator;
import io.nop.commons.collections.iterator.SelectNextIterator;
import io.nop.commons.util.StringHelper;
import io.nop.dao.exceptions.DaoException;
import io.nop.dao.exceptions.UnknownEntityException;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.nop.dao.DaoErrors.ARG_ENTITY_NAME;
import static io.nop.dao.DaoErrors.ERR_DAO_MISSING_ENTITY_WITH_PROPS;

/**
 * 针对单一类型的实体对象的数据访问对象接口。一般一个EntityDao对应一个数据库表
 */
public interface IEntityDao<T extends IDaoEntity> {
    /**
     * 实体对象的全名，一般对应于类名
     */
    String getEntityName();

    String getTableName();

    String getDeleteFlagProp();

    boolean isUseLogicalDelete();

    /**
     * 将除了主键之外的所有属性值重置为缺省值
     */
    void resetToDefaultValues(T entity);

    List<String> getPkColumnNames();

    String getEntityClassName();

    /**
     * 将字符串格式的id转换为合适的属性类型，例如转换为复合主键对象
     *
     * @param id 实体主键
     * @return 已经转换为合适类型的主键值
     */
    Object castId(Object id);

    List<Object> castIdList(Collection<?> ids);

    /**
     * 初始化实体主键。如果实体当前主键为空，而且设置了主键生成策略，则按照该策略设置实体主键
     *
     * @param entity 实体对象
     * @return 实体对象
     */
    Object initEntityId(T entity);

    /**
     * 获取实体的id
     *
     * @param entity 实体对象
     * @return 实体id
     */
    Object getEntityId(T entity);

    /**
     * 创建一个新的实体对象以供使用
     *
     * @return 新建的实体对象
     */
    T newEntity();

    void saveEntity(T entity);

    void updateEntity(T entity);

    void saveOrUpdateEntity(T entity);

    void deleteEntity(T entity);

    /**
     * 根据id返回实体，有可能只是返回一个proxy而没有真正去查询数据库
     *
     * @param id 实体主键
     * @return 返回值不会为null
     */
    T loadEntityById(Object id);

    /**
     * 使用select for update来锁定单条记录。如果数据库中不存在对应记录则抛出异常
     *
     * @param entity 实体对象
     */
    void lockEntity(T entity) throws UnknownEntityException;

    /**
     * 通过主键返回一个唯一的对象。如果数据库中不存在，则返回null。如果内存中存在proxy对象，则直接返回
     *
     * @param id 实体主键
     * @return 实体对象
     */
    T getEntityById(Object id);

    /**
     * 通过主键返回一个不为空的数据库对象
     *
     * @param id 实体主键
     * @return 实体对象
     */
    default T requireEntityById(Object id) {
        T entity = getEntityById(id);
        if (entity == null)
            throw new UnknownEntityException(getEntityName(), id);
        return entity;
    }

    List<T> batchGetEntitiesByIds(Collection<?> ids);

    List<T> batchRequireEntitiesByIds(Collection<?> ids);

    /**
     * 根据主键加载实体，返回主键到实体对象的映射集合。
     *
     * @param ids 主键列表。
     * @return 如果主键对应的实体不存在，则集合中没有对应元素
     */
    Map<Object, T> batchGetEntityMapByIds(Collection<?> ids);

    /**
     * 将detached的实体重新与session关联
     */
    void attachEntity(T entity);

    void batchFlush(Collection<T> entities);

    void batchSaveEntities(Collection<T> entities);

    void batchUpdateEntities(Collection<T> entities);

    void batchDeleteEntities(Collection<T> entities);

    void batchGetEntities(Collection<T> entities);

    long deleteByExample(T example);

    /**
     * 判断表中是否存在记录
     */
    boolean isEmpty();

    T findFirstByExample(T example);

    long countByExample(T example);

    default T requireFirstByExample(T example) {
        T ret = findFirstByExample(example);
        if (ret == null) {
            throw new DaoException(ERR_DAO_MISSING_ENTITY_WITH_PROPS).param(ARG_ENTITY_NAME, getEntityName())
                    .params(example.orm_initedValues());
        }
        return ret;
    }

    List<T> findPageByExample(T example, List<OrderFieldBean> orderBy, long offset, int limit);

    List<T> findAllByExample(T example, List<OrderFieldBean> orderBy);

    default List<T> findAllByExample(T example) {
        return findAllByExample(example, null);
    }

    T findFirstByQuery(QueryBean query);

    long countByQuery(QueryBean query);

    long deleteByQuery(QueryBean query);

    List<T> findPageByQuery(QueryBean query);

    void findPageAndReturnCursor(QueryBean query, PageBean<T> page);

    List<T> findAllByQuery(QueryBean query);

    boolean existsByQuery(QueryBean query);

    /**
     * 查找所有具有指定属性值的实体。例如 findAllByProps(list,["a","b"]) 对应于查询 (a=xx and b=yy or ...)
     *
     * @param list      对象列表，从中取到它每个条目的属性值作为查询条件。
     * @param propNames 作为查询条件的属性名
     */
    List<T> findAllByProps(List<Object> list, List<String> propNames);

    long updateByQuery(QueryBean query, Map<String, Object> props);

    List<T> findAll();

    /**
     * 查找在lastEntity之后的n条记录，用于下一页这种形式的分页查询。 类似于 select o from entity o where o.id > lastEntity.id limit 10
     *
     * @param lastEntity 上一页的最后一条记录
     * @param filter     过滤条件
     * @param orderBy    排序条件
     * @param limit      最多取多少条记录
     */
    List<T> findNext(T lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit);

    default List<T> findNext(QueryBean query) {
        String cursor = query.getCursor();
        if (StringHelper.isEmpty(cursor)) {
            return findNext(null, query.getFilter(), query.getOrderBy(), query.getLimit());
        }
        T lastEntity = loadEntityByCursor(cursor);
        return findNext(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit());
    }

    List<T> findPrev(T lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit);

    default List<T> findPrev(QueryBean query) {
        String cursor = query.getCursor();
        if (StringHelper.isEmpty(cursor)) {
            return findPrev(null, query.getFilter(), query.getOrderBy(), query.getLimit());
        }
        T lastEntity = loadEntityByCursor(cursor);
        return findPrev(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit());
    }

    T loadEntityByCursor(String cursor);

    default Iterator<T> iterator(QueryBean query) {
        return new SelectNextIterator<>(
                lastEntity -> findNext(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit()));
    }

    default Iterator<List<T>> pageIterator(QueryBean query) {
        return new FindNextPageIterator<>(
                lastEntity -> findNext(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit()));
    }

    default void forEachEntity(QueryBean query, Consumer<T> consumer) {
        iterator(query).forEachRemaining(consumer);
    }

    /**
     * 得到属性对象所对应的dao。例如userDao.getPropDao("dept")返回User对象的关联表Dept表所对应的IEntityDao
     *
     * @param propName 属性名称，可以是复合属性，例如 user.dept
     * @param <R>      属性对象的类型
     * @return 属性对象所对应的dao
     */
    <R extends IDaoEntity> IEntityDao<R> propDao(String propName);

    void batchLoadProps(Collection<T> entities, Collection<String> propNames);

    /**
     * 将一级缓存中的修改刷新到数据库中
     */
    void flushSession();

    /**
     * 从session中清除所有本实体类的实体
     */
    void clearEntitySessionCache();

    /**
     * 清空本实体类对应的二级缓存
     */
    void clearEntityGlobalCache();
}