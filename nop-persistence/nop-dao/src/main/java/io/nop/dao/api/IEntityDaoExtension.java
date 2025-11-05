/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.api;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;

import java.util.List;
import java.util.Map;

public interface IEntityDaoExtension<T extends IDaoEntity> {
    boolean supportReadQuery(QueryBean query);

    boolean supportUpdateQuery(QueryBean query);

    boolean supportDeleteQuery(QueryBean query);

    boolean supportForEachQuery(QueryBean query);

    boolean supportFindNext(ITreeBean filter, List<OrderFieldBean> orderBy);

    /**
     * 查询满足条件的第一个实体
     *
     * @param query
     * @return
     */
    <V> V findFirst(QueryBean query);

    /**
     * 查询满足条件的所有实体
     *
     * @param query
     * @return
     */
    <V> List<V> findPage(QueryBean query);

    <V> List<V> findAll(QueryBean query);

    List<T> findNext(T lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit);

    List<T> findPrev(T firstEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit);

    /**
     * 满足条件的记录条数
     *
     * @param query
     * @return
     */
    long count(QueryBean query);

    /**
     * 判断是否存在满足条件的记录
     *
     * @param query
     * @return
     */
    boolean exists(QueryBean query);

    /**
     * 删除所有满足条件的记录
     *
     * @param query
     * @return
     */
    long delete(QueryBean query);

    /**
     * 更新所有满足条件的记录
     *
     * @param query
     * @param props 需要更新的属性
     * @return
     */
    long update(QueryBean query, Map<String, Object> props);
}