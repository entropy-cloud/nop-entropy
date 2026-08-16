package io.nop.datav.service.entity;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.orm.IOrmEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.datav.service.NopDatavErrors.ARG_ACTION;
import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;

/**
 * 单写点实体的 CrudBizModel 基类（P1-10 修复，plan 2026-08-15-2146-1，裁定 D5 方案 c）。
 *
 * <p>快照表（DashboardSnapshot/ScreenSnapshot，append-only，唯一写入点 publish/rollback）与
 * AlertState（状态机，唯一写入点 AlertEvaluator）不暴露标准 CRUD mutation 面：本基类覆写
 * {@link CrudBizModel} 的全部 13 个标准 mutation 入口（save/saveOrUpdate/copyForNew/update/
 * batchUpdate/updateByQuery/batchModify/delete/batchDelete/deleteByQuery/m2m 三件套），一律抛
 * 子类提供的结构化 {@link NopException}——显式拒绝而非静默移除（plan guide 规则 24 标准做法；
 * GraphQL operation 面仍可解析，但调用即收到结构化错误码，负向测试经 IGraphQLEngine 全链锚定）。</p>
 *
 * <p>领域写入点不受影响：publish/rollback/AlertEvaluator 全部经 DAO 直写
 * （{@code saveEntityDirectly}/{@code updateEntityDirectly}/JDBC 直更），不经这些 mutation action。</p>
 */
public abstract class NopDatavSingleWriterCrudBizModel<T extends IOrmEntity> extends CrudBizModel<T> {

    /**
     * 子类提供的拒绝错误码（快照类与 AlertState 语义不同，分别携带各自的说明与正确写入口）。
     */
    protected abstract ErrorCode stdMutationNotAllowedError();

    private NopException reject(String action) {
        return new NopException(stdMutationNotAllowedError())
                .param(ARG_ACTION, action)
                .param(ARG_BIZ_OBJ_NAME, getBizObjName());
    }

    @Override
    public T save(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw reject("save");
    }

    @Override
    public T saveOrUpdate(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw reject("saveOrUpdate");
    }

    @Override
    public T copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw reject("copyForNew");
    }

    @Override
    public T update(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw reject("update");
    }

    @Override
    public void batchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data,
                            @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                            IServiceContext context) {
        throw reject("batchUpdate");
    }

    @Override
    public int updateByQuery(@Name("query") io.nop.api.core.beans.query.QueryBean query,
                              @Name("data") Map<String, Object> data, IServiceContext context) {
        throw reject("updateByQuery");
    }

    @Override
    public void batchModify(@Name("data") List<Map<String, Object>> data,
                            @Optional @Name("common") Map<String, Object> common,
                            @Optional @Name("delIds") @Description("@i18n:biz.delIds|待删除的实体主键列表") Set<String> delIds,
                            IServiceContext context) {
        throw reject("batchModify");
    }

    @Override
    public boolean delete(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id, IServiceContext context) {
        throw reject("delete");
    }

    @Override
    public Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
        throw reject("batchDelete");
    }

    @Override
    public int deleteByQuery(@Name("query") io.nop.api.core.beans.query.QueryBean query, IServiceContext context) {
        throw reject("deleteByQuery");
    }

    @Override
    public void addManyToManyRelations(@Name("id") String id, @Name("propName") String propName,
                                       @Name("relValues") Collection<String> relValues,
                                       @Optional @Name("filter") TreeBean filter,
                                       IServiceContext context) {
        throw reject("addManyToManyRelations");
    }

    @Override
    public void removeManyToManyRelations(@Name("id") String id, @Name("propName") String propName,
                                          @Name("relValues") Collection<String> relValues,
                                          @Optional @Name("filter") TreeBean filter,
                                          IServiceContext context) {
        throw reject("removeManyToManyRelations");
    }

    @Override
    public void updateManyToManyRelations(@Name("id") String id, @Name("propName") String propName,
                                          @Name("relValues") Collection<String> relValues,
                                          @Optional @Name("filter") TreeBean filter, IServiceContext context) {
        throw reject("updateManyToManyRelations");
    }
}
