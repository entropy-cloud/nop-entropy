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
import io.nop.api.core.util.ICancelToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD API接口，用于表示CrudBizModel远程调用时客户端可用的接口。
 *
 * @param <I> 输入类型（如 XxxInputBean）
 * @param <O> 输出类型（如 XxxOutputBean）
 */
public interface ICrudApi<I, O> {

    // ==================== 查询操作 ====================

    @BizQuery
    long findCount(@Optional @Name("query") QueryBean query, ICancelToken cancelToken);

    default long findCount(@Optional @Name("query") QueryBean query) {
        return findCount(query, null);
    }

    @BizQuery
    PageBean<O> findPage(@Optional @Name("query") QueryBean query,
                         @Optional @Name("selection") FieldSelectionBean selection,
                         ICancelToken cancelToken);

    default PageBean<O> findPage(@Optional @Name("query") QueryBean query) {
        return findPage(query, null, null);
    }

    default PageBean<O> findPage(@Optional @Name("query") QueryBean query,
                                 @Optional @Name("selection") FieldSelectionBean selection) {
        return findPage(query, selection, null);
    }

    @BizQuery
    O findFirst(@Optional @Name("query") QueryBean query,
                @Optional @Name("selection") FieldSelectionBean selection,
                ICancelToken cancelToken);

    default O findFirst(@Optional @Name("query") QueryBean query) {
        return findFirst(query, null, null);
    }

    default O findFirst(@Optional @Name("query") QueryBean query,
                        @Optional @Name("selection") FieldSelectionBean selection) {
        return findFirst(query, selection, null);
    }

    @BizQuery
    List<O> findList(@Optional @Name("query") QueryBean query,
                     @Optional @Name("selection") FieldSelectionBean selection,
                     ICancelToken cancelToken);

    default List<O> findList(@Optional @Name("query") QueryBean query) {
        return findList(query, null, null);
    }

    default List<O> findList(@Optional @Name("query") QueryBean query,
                             @Optional @Name("selection") FieldSelectionBean selection) {
        return findList(query, selection, null);
    }

    @BizQuery
    O get(@Name("id") String id,
          @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
          FieldSelectionBean selection,
          ICancelToken cancelToken);

    default O get(@Name("id") String id) {
        return get(id, false, null, null);
    }

    default O get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        return get(id, ignoreUnknown, null, null);
    }

    default O get(@Name("id") String id, FieldSelectionBean selection) {
        return get(id, false, selection, null);
    }

    @BizQuery
    List<O> batchGet(@Name("ids") Collection<String> ids,
                     @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                     FieldSelectionBean selection,
                     ICancelToken cancelToken);

    default List<O> batchGet(@Name("ids") Collection<String> ids) {
        return batchGet(ids, false, null, null);
    }

    default List<O> batchGet(@Name("ids") Collection<String> ids,
                             @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        return batchGet(ids, ignoreUnknown, null, null);
    }

    default List<O> batchGet(@Name("ids") Collection<String> ids, FieldSelectionBean selection) {
        return batchGet(ids, false, selection, null);
    }

    @BizQuery
    DictBean asDict(ICancelToken cancelToken);

    default DictBean asDict() {
        return asDict(null);
    }

    // ==================== 新增操作 ====================

    @BizMutation
    O save(@Name("data") I data, FieldSelectionBean selection, ICancelToken cancelToken);

    default O save(@Name("data") I data) {
        return save(data, null, null);
    }

    default O save(@Name("data") I data, FieldSelectionBean selection) {
        return save(data, selection, null);
    }

    @BizMutation
    O saveOrUpdate(@Name("data") I data, FieldSelectionBean selection, ICancelToken cancelToken);

    default O saveOrUpdate(@Name("data") I data) {
        return saveOrUpdate(data, null, null);
    }

    default O saveOrUpdate(@Name("data") I data, FieldSelectionBean selection) {
        return saveOrUpdate(data, selection, null);
    }

    @BizMutation
    O copyForNew(@Name("data") I data, FieldSelectionBean selection, ICancelToken cancelToken);

    default O copyForNew(@Name("data") I data) {
        return copyForNew(data, null, null);
    }

    default O copyForNew(@Name("data") I data, FieldSelectionBean selection) {
        return copyForNew(data, selection, null);
    }

    // ==================== 修改操作 ====================

    @BizMutation
    O update(@Name("data") I data, FieldSelectionBean selection, ICancelToken cancelToken);

    default O update(@Name("data") I data) {
        return update(data, null, null);
    }

    default O update(@Name("data") I data, FieldSelectionBean selection) {
        return update(data, selection, null);
    }

    @BizMutation
    void batchUpdate(@Name("ids") Set<String> ids,
                     @Name("data") I data,
                     @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                     ICancelToken cancelToken);

    default void batchUpdate(@Name("ids") Set<String> ids, @Name("data") I data) {
        batchUpdate(ids, data, false, null);
    }

    default void batchUpdate(@Name("ids") Set<String> ids,
                             @Name("data") I data,
                             @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        batchUpdate(ids, data, ignoreUnknown, null);
    }

    @BizMutation
    int updateByQuery(@Name("query") QueryBean query,
                      @Name("data") I data,
                      ICancelToken cancelToken);

    default int updateByQuery(@Name("query") QueryBean query, @Name("data") I data) {
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
    void batchModify(@Name("data") List<I> data,
                     @Optional @Name("common") I common,
                     @Optional @Name("delIds") Set<String> delIds,
                     ICancelToken cancelToken);

    default void batchModify(@Name("data") List<I> data) {
        batchModify(data, null, null, null);
    }

    default void batchModify(@Name("data") List<I> data,
                             @Optional @Name("common") I common,
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

    // ==================== 逻辑删除操作 ====================

    @BizQuery
    PageBean<O> deleted_findPage(@Optional @Name("query") QueryBean query,
                                 @Optional @Name("selection") FieldSelectionBean selection,
                                 ICancelToken cancelToken);

    default PageBean<O> deleted_findPage(@Optional @Name("query") QueryBean query) {
        return deleted_findPage(query, null, null);
    }

    default PageBean<O> deleted_findPage(@Optional @Name("query") QueryBean query,
                                         @Optional @Name("selection") FieldSelectionBean selection) {
        return deleted_findPage(query, selection, null);
    }

    @BizQuery
    O deleted_get(@Name("id") String id,
                  @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                  FieldSelectionBean selection,
                  ICancelToken cancelToken);

    default O deleted_get(@Name("id") String id) {
        return deleted_get(id, false, null, null);
    }

    default O deleted_get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown) {
        return deleted_get(id, ignoreUnknown, null, null);
    }

    default O deleted_get(@Name("id") String id, FieldSelectionBean selection) {
        return deleted_get(id, false, selection, null);
    }

    @BizQuery
    O recoverDeleted(@Name("id") String id, FieldSelectionBean selection, ICancelToken cancelToken);

    default O recoverDeleted(@Name("id") String id) {
        return recoverDeleted(id, null, null);
    }

    default O recoverDeleted(@Name("id") String id, FieldSelectionBean selection) {
        return recoverDeleted(id, selection, null);
    }
}
