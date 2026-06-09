/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.biz;

import io.nop.api.core.annotations.biz.BizAction;
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
import io.nop.core.context.IServiceContext;
import io.nop.orm.IOrmEntity;
import io.nop.xlang.xmeta.IObjMeta;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD business interface for entity operations.
 * <T> entity type. @BizQuery/@BizMutation are GraphQL-callable; @BizAction is internal.
 */
public interface ICrudBiz<T extends IOrmEntity> extends IOrmEntityBiz {

    // ---- Query ----

    @BizQuery
    long findCount(@Optional @Name("query") QueryBean query, IServiceContext context);

    /** Returns PageBean with items + total. Supports offset/limit/filter/orderBy via QueryBean. */
    @BizQuery
    PageBean<T> findPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /** Returns first matching record, or null. */
    @BizQuery
    T findFirst(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /** Unlike findPage, returns List (no total count). Subject to default max limit. */
    @BizQuery
    List<T> findList(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /** ignoreUnknown=true returns null if not found; false throws UnknownEntityException. */
    @BizQuery
    T get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    @BizQuery
    List<T> batchGet(@Name("ids") Collection<String> ids, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    /** Returns entity as DictBean (id→displayName), for dropdowns. */
    @BizQuery
    DictBean asDict(IServiceContext context);

    // ---- Create ----

    /** Creates new record from data map. Throws if id already exists. */
    @BizMutation
    T save(@Name("data") Map<String, Object> data, IServiceContext context);

    /** No id → save; has id → update. */
    @BizMutation
    T saveOrUpdate(@Name("data") Map<String, Object> data, IServiceContext context);

    /** Copies existing record (source id in data) to new record; PK/serials regenerated. */
    @BizMutation
    T copyForNew(@Name("data") Map<String, Object> data, IServiceContext context);

    // ---- Update ----

    /** Updates record by id in data map. */
    @BizMutation
    T update(@Name("data") Map<String, Object> data, IServiceContext context);

    /** Applies same data fields to all records matching ids. */
    @BizMutation
    void batchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data,
                     @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    /** Updates all records matching query with data fields. Returns updated count. */
    @BizMutation
    int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data, IServiceContext context);

    // ---- Delete ----

    /** Logical delete if configured; cascades to children if configured. Returns false if not found. */
    @BizMutation
    boolean delete(@Name("id") String id, IServiceContext context);

    /** Returns set of ids that could not be deleted. */
    @BizMutation
    Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context);

    /** Batch add/update/delete in one call. data=no-id→save, data=has-id→update, delIds→delete. common merged into each data row. */
    @BizMutation
    void batchModify(@Name("data") List<Map<String, Object>> data, @Optional @Name("common") Map<String, Object> common,
                     @Optional @Name("delIds") Set<String> delIds, IServiceContext context);

    /** Deletes all records matching query. Returns deleted count. */
    @BizMutation
    int deleteByQuery(@Name("query") QueryBean query, IServiceContext context);

    // ---- Many-to-Many ----

    @BizMutation
    void addManyToManyRelations(@Name("id") String id, @Name("propName") String propName,
                                @Name("relValues") Collection<String> relValues,
                                @Optional @Name("filter") TreeBean filter, IServiceContext context);

    @BizMutation
    void removeManyToManyRelations(@Name("id") String id, @Name("propName") String propName,
                                   @Name("relValues") Collection<String> relValues,
                                   @Optional @Name("filter") TreeBean filter, IServiceContext context);

    /** Replaces all relations for propName with relValues. */
    @BizMutation
    void updateManyToManyRelations(@Name("id") String id, @Name("propName") String propName,
                                   @Name("relValues") Collection<String> relValues,
                                   @Optional @Name("filter") TreeBean filter, IServiceContext context);

    // ---- Tree ----

    /** Returns root nodes (no parent) for tree-structured entities. */
    @BizQuery
    List<T> findRoots(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    @BizQuery
    PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    @BizQuery
    List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /** Like findTreeEntityList but returns full entity objects. */
    @BizQuery
    List<T> findListForTree(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    @BizQuery
    PageBean<T> findPageForTree(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    // ---- Soft-delete ----

    @BizQuery
    PageBean<T> deleted_findPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    @BizQuery
    T deleted_get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    /** Clears soft-delete flag. */
    @BizQuery
    T recoverDeleted(@Name("id") String id, IServiceContext context);

    // ---- Helpers ----

    T newEntity();

    IObjMeta getObjMeta();

    String getBizObjName();

    // ---- Entity-level operations (@BizAction, internal pipeline) ----

    /** Gets entity by id or throws. action used for auth check. */
    @BizAction
    T requireEntity(@Name("id") String id, @Name("action") String action, IServiceContext context);

    @BizAction
    void deleteEntity(@Name("entity") T entity, @Optional @Name("action") String action, IServiceContext context);

    @BizAction
    void saveEntity(@Name("entity") T entity, @Optional @Name("action") String action, IServiceContext context);

    @BizAction
    void updateEntity(@Name("entity") T entity, @Optional @Name("action") String action, IServiceContext context);

    /** Copies data map fields onto entity. Supports master-detail. */
    @BizAction
    void assignToEntity(@Name("entity") T entity, @Name("data") Map<String, Object> data,
                        @Optional @Name("action") String action, IServiceContext context);

    /** Builds entity from data map. action="save" for new, "update" for existing. */
    @BizAction
    T buildEntityForSave(@Name("data") Map<String, Object> data,
                         @Optional @Name("action") String action, IServiceContext context);

    /** Throws if current user cannot access entity for given action. */
    @BizAction
    void checkAllowAccess(@Name("entity") T entity, @Optional @Name("action") String action, IServiceContext context);

    void batchLoadSelection(@Name("entityList") Collection<T> entityList, @Name("selection") FieldSelectionBean selection, IServiceContext context);

    /** Reads fields from entity via GraphQL engine (uses XMeta field mapping, not direct field access). */
    @BizAction
    Map<String, Object> fetchSelection(@Name("entity") T entity, @Name("selection") FieldSelectionBean selection, IServiceContext context);

    @BizAction
    List<Map<String, Object>> fetchSelectionForList(@Name("entityList") Collection<T> entityList, @Name("selection") FieldSelectionBean selection, IServiceContext context);
}
