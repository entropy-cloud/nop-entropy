/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.api;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.std.StdTreeEntity;
import io.nop.api.core.util.ICancelToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD API接口，用于表示CrudBizModel远程调用时客户端可用的接口。
 *
 * @param <T> 实体类型
 */
public interface ICrudApi<T> {

    // ==================== 查询操作 ====================

    @BizQuery
    long findCount(@Optional @Name("query") QueryBean query, ICancelToken cancelToken);

    default long findCount(@Optional @Name("query") QueryBean query) {
        return findCount(query, null);
    }

    @BizQuery
    PageBean<T> findPage(@Optional @Name("query") QueryBean query,
                         @Optional @Name("selection") FieldSelectionBean selection,
                         ICancelToken cancelToken);

    default PageBean<T> findPage(@Optional @Name("query") QueryBean query) {
        return findPage(query, null, null);
    }

    default PageBean<T> findPage(@Optional @Name("query") QueryBean query,
                                 @Optional @Name("selection") FieldSelectionBean selection) {
        return findPage(query, selection, null);
    }

    @BizQuery
    T findFirst(@Optional @Name("query") QueryBean query,
                @Optional @Name("selection") FieldSelectionBean selection,
                ICancelToken cancelToken);

    default T findFirst(@Optional @Name("query") QueryBean query) {
        return findFirst(query, null, null);
    }

    default T findFirst(@Optional @Name("query") QueryBean query,
                        @Optional @Name("selection") FieldSelectionBean selection) {
        return findFirst(query, selection, null);
    }

    @BizQuery
    List<T> findList(@Optional @Name("query") QueryBean query,
                     @Optional @Name("selection") FieldSelectionBean selection,
                     ICancelToken cancelToken);

    default List<T> findList(@Optional @Name("query") QueryBean query) {
        return findList(query, null, null);
    }

    default List<T> findList(@Optional @Name("query") QueryBean query,
                             @Optional @Name("selection") FieldSelectionBean selection) {
        return findList(query, selection, null);
    }

    @BizQuery
    T get(@Name("id") String id,
          @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
          FieldSelectionBean selection,
          ICancelToken cancelToken);

    default T get(@Name("id") String id) {
        return get(id, false, null, null);
    }

    default T get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        return get(id, ignoreUnknown, null, null);
    }

    default T get(@Name("id") String id, FieldSelectionBean selection) {
        return get(id, false, selection, null);
    }

    @BizQuery
    List<T> batchGet(@Name("ids") Collection<String> ids,
                     @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                     FieldSelectionBean selection,
                     ICancelToken cancelToken);

    default List<T> batchGet(@Name("ids") Collection<String> ids) {
        return batchGet(ids, false, null, null);
    }

    default List<T> batchGet(@Name("ids") Collection<String> ids,
                             @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        return batchGet(ids, ignoreUnknown, null, null);
    }

    default List<T> batchGet(@Name("ids") Collection<String> ids, FieldSelectionBean selection) {
        return batchGet(ids, false, selection, null);
    }

    @BizQuery
    DictBean asDict(ICancelToken cancelToken);

    default DictBean asDict() {
        return asDict(null);
    }

    // ==================== 新增操作 ====================

    @BizMutation
    T save(@Name("data") Map<String, Object> data, FieldSelectionBean selection, ICancelToken cancelToken);

    default T save(@Name("data") Map<String, Object> data) {
        return save(data, null, null);
    }

    default T save(@Name("data") Map<String, Object> data, FieldSelectionBean selection) {
        return save(data, selection, null);
    }

    @BizMutation
    T saveOrUpdate(@Name("data") Map<String, Object> data, FieldSelectionBean selection, ICancelToken cancelToken);

    default T saveOrUpdate(@Name("data") Map<String, Object> data) {
        return saveOrUpdate(data, null, null);
    }

    default T saveOrUpdate(@Name("data") Map<String, Object> data, FieldSelectionBean selection) {
        return saveOrUpdate(data, selection, null);
    }

    @BizMutation
    T copyForNew(@Name("data") Map<String, Object> data, FieldSelectionBean selection, ICancelToken cancelToken);

    default T copyForNew(@Name("data") Map<String, Object> data) {
        return copyForNew(data, null, null);
    }

    default T copyForNew(@Name("data") Map<String, Object> data, FieldSelectionBean selection) {
        return copyForNew(data, selection, null);
    }

    // ==================== 修改操作 ====================

    @BizMutation
    T update(@Name("data") Map<String, Object> data, FieldSelectionBean selection, ICancelToken cancelToken);

    default T update(@Name("data") Map<String, Object> data) {
        return update(data, null, null);
    }

    default T update(@Name("data") Map<String, Object> data, FieldSelectionBean selection) {
        return update(data, selection, null);
    }

    @BizMutation
    void batchUpdate(@Name("ids") Set<String> ids,
                     @Name("data") Map<String, Object> data,
                     @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                     ICancelToken cancelToken);

    default void batchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data) {
        batchUpdate(ids, data, false, null);
    }

    default void batchUpdate(@Name("ids") Set<String> ids,
                             @Name("data") Map<String, Object> data,
                             @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        batchUpdate(ids, data, ignoreUnknown, null);
    }

    @BizMutation
    int updateByQuery(@Name("query") QueryBean query,
                      @Name("data") Map<String, Object> data,
                      ICancelToken cancelToken);

    default int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data) {
        return updateByQuery(query, data, null);
    }

    // ==================== 删除操作 ====================

    @BizMutation
    boolean delete(@Name("id") String id, ICancelToken cancelToken);

    default boolean delete(@Name("id") String id) {
        return delete(id, null);
    }

    @BizMutation
    Set<String> batchDelete(@Name("ids") Set<String> ids, ICancelToken cancelToken);

    default Set<String> batchDelete(@Name("ids") Set<String> ids) {
        return batchDelete(ids, null);
    }

    @BizMutation
    void batchModify(@Name("data") List<Map<String, Object>> data,
                     @Optional @Name("common") Map<String, Object> common,
                     @Optional @Name("delIds") Set<String> delIds,
                     ICancelToken cancelToken);

    default void batchModify(@Name("data") List<Map<String, Object>> data) {
        batchModify(data, null, null, null);
    }

    default void batchModify(@Name("data") List<Map<String, Object>> data,
                             @Optional @Name("common") Map<String, Object> common,
                             @Optional @Name("delIds") Set<String> delIds) {
        batchModify(data, common, delIds, null);
    }

    @BizMutation
    int deleteByQuery(@Name("query") QueryBean query, ICancelToken cancelToken);

    default int deleteByQuery(@Name("query") QueryBean query) {
        return deleteByQuery(query, null);
    }

    // ==================== 多对多关联操作 ====================

    @BizMutation
    void addManyToManyRelations(@Name("id") String id,
                                @Name("propName") String propName,
                                @Name("relValues") Collection<String> relValues,
                                @Optional @Name("filter") TreeBean filter,
                                ICancelToken cancelToken);

    default void addManyToManyRelations(@Name("id") String id,
                                        @Name("propName") String propName,
                                        @Name("relValues") Collection<String> relValues) {
        addManyToManyRelations(id, propName, relValues, null, null);
    }

    default void addManyToManyRelations(@Name("id") String id,
                                        @Name("propName") String propName,
                                        @Name("relValues") Collection<String> relValues,
                                        @Optional @Name("filter") TreeBean filter) {
        addManyToManyRelations(id, propName, relValues, filter, null);
    }

    @BizMutation
    void removeManyToManyRelations(@Name("id") String id,
                                   @Name("propName") String propName,
                                   @Name("relValues") Collection<String> relValues,
                                   @Optional @Name("filter") TreeBean filter,
                                   ICancelToken cancelToken);

    default void removeManyToManyRelations(@Name("id") String id,
                                           @Name("propName") String propName,
                                           @Name("relValues") Collection<String> relValues) {
        removeManyToManyRelations(id, propName, relValues, null, null);
    }

    default void removeManyToManyRelations(@Name("id") String id,
                                           @Name("propName") String propName,
                                           @Name("relValues") Collection<String> relValues,
                                           @Optional @Name("filter") TreeBean filter) {
        removeManyToManyRelations(id, propName, relValues, filter, null);
    }

    @BizMutation
    void updateManyToManyRelations(@Name("id") String id,
                                   @Name("propName") String propName,
                                   @Name("relValues") Collection<String> relValues,
                                   @Optional @Name("filter") TreeBean filter,
                                   ICancelToken cancelToken);

    default void updateManyToManyRelations(@Name("id") String id,
                                           @Name("propName") String propName,
                                           @Name("relValues") Collection<String> relValues) {
        updateManyToManyRelations(id, propName, relValues, null, null);
    }

    default void updateManyToManyRelations(@Name("id") String id,
                                           @Name("propName") String propName,
                                           @Name("relValues") Collection<String> relValues,
                                           @Optional @Name("filter") TreeBean filter) {
        updateManyToManyRelations(id, propName, relValues, filter, null);
    }

    // ==================== 树形结构操作 ====================

    @BizQuery
    List<T> findRoots(@Optional @Name("query") QueryBean query,
                      @Optional @Name("selection") FieldSelectionBean selection,
                      ICancelToken cancelToken);

    default List<T> findRoots(@Optional @Name("query") QueryBean query) {
        return findRoots(query, null, null);
    }

    default List<T> findRoots(@Optional @Name("query") QueryBean query,
                              @Optional @Name("selection") FieldSelectionBean selection) {
        return findRoots(query, selection, null);
    }

    @BizQuery
    PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query,
                                               @Optional @Name("selection") FieldSelectionBean selection,
                                               ICancelToken cancelToken);

    default PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query) {
        return findTreeEntityPage(query, null, null);
    }

    default PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query,
                                                       @Optional @Name("selection") FieldSelectionBean selection) {
        return findTreeEntityPage(query, selection, null);
    }

    @BizQuery
    List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query,
                                           @Optional @Name("selection") FieldSelectionBean selection,
                                           ICancelToken cancelToken);

    default List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query) {
        return findTreeEntityList(query, null, null);
    }

    default List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query,
                                                   @Optional @Name("selection") FieldSelectionBean selection) {
        return findTreeEntityList(query, selection, null);
    }

    @BizQuery
    List<T> findListForTree(@Optional @Name("query") QueryBean query,
                            @Optional @Name("selection") FieldSelectionBean selection,
                            ICancelToken cancelToken);

    default List<T> findListForTree(@Optional @Name("query") QueryBean query) {
        return findListForTree(query, null, null);
    }

    default List<T> findListForTree(@Optional @Name("query") QueryBean query,
                                    @Optional @Name("selection") FieldSelectionBean selection) {
        return findListForTree(query, selection, null);
    }

    @BizQuery
    PageBean<T> findPageForTree(@Optional @Name("query") QueryBean query,
                                @Optional @Name("selection") FieldSelectionBean selection,
                                ICancelToken cancelToken);

    default PageBean<T> findPageForTree(@Optional @Name("query") QueryBean query) {
        return findPageForTree(query, null, null);
    }

    default PageBean<T> findPageForTree(@Optional @Name("query") QueryBean query,
                                        @Optional @Name("selection") FieldSelectionBean selection) {
        return findPageForTree(query, selection, null);
    }

    // ==================== 逻辑删除操作 ====================

    @BizQuery
    PageBean<T> deleted_findPage(@Optional @Name("query") QueryBean query,
                                 @Optional @Name("selection") FieldSelectionBean selection,
                                 ICancelToken cancelToken);

    default PageBean<T> deleted_findPage(@Optional @Name("query") QueryBean query) {
        return deleted_findPage(query, null, null);
    }

    default PageBean<T> deleted_findPage(@Optional @Name("query") QueryBean query,
                                         @Optional @Name("selection") FieldSelectionBean selection) {
        return deleted_findPage(query, selection, null);
    }

    @BizQuery
    T deleted_get(@Name("id") String id,
                  @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                  FieldSelectionBean selection,
                  ICancelToken cancelToken);

    default T deleted_get(@Name("id") String id) {
        return deleted_get(id, false, null, null);
    }

    default T deleted_get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        return deleted_get(id, ignoreUnknown, null, null);
    }

    default T deleted_get(@Name("id") String id, FieldSelectionBean selection) {
        return deleted_get(id, false, selection, null);
    }

    @BizQuery
    T recoverDeleted(@Name("id") String id, FieldSelectionBean selection, ICancelToken cancelToken);

    default T recoverDeleted(@Name("id") String id) {
        return recoverDeleted(id, null, null);
    }

    default T recoverDeleted(@Name("id") String id, FieldSelectionBean selection) {
        return recoverDeleted(id, selection, null);
    }
}
