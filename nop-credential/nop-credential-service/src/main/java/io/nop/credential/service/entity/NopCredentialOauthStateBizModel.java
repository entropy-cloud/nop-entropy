package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.credential.biz.INopCredentialOauthStateBiz;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredentialOauthState;
import io.nop.credential.service.CredentialOwnership;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

/**
 * {@code NopCredentialOauthState} 的查询面 BizModel（A1-audit D2-01/D4-01 修复，2026-08-17）。
 *
 * <p>state 绑定表为 <b>OAuth 引擎内部存储</b>（设计 §八 W9-impl 裁定；owner doc 核心实体表）：
 * 唯一合法写方是引擎 {@code NopCredentialOauthStateStore}（dao 直写，不经本面），唯一合法读方是
 * 引擎回调链。codegen 生成的裸 CRUD 管理面构成旁路：普通登录用户可篡改/删除在途 state 行
 * （token 落点劫持 + 授权流 DoS），本类按 {@link NopCredentialUsageBizModel} 先例收口：
 *
 * <ul>
 * <li>查询面限管理员（双层防御：action-auth 资源收紧 admin + 本类运行时判定；无登录态同样拒绝）。</li>
 * <li>全部标准 mutation 旁路禁用（save/update/delete/batchDelete/updateByQuery/deleteByQuery/copyForNew，
 *     对齐 {@code NopCredentialAuthBizModel} 七动作先例）——引擎写路径不经 xmeta/BizModel，零影响。</li>
 * </ul>
 */
@BizModel("NopCredentialOauthState")
public class NopCredentialOauthStateBizModel extends CrudBizModel<NopCredentialOauthState>
        implements INopCredentialOauthStateBiz {
    public NopCredentialOauthStateBizModel(){
        setEntityName(NopCredentialOauthState.class.getName());
    }

    /**
     * 查询面管理员判定（findPage/findList/findFirst/findCount 统一经 defaultPrepareQuery）。
     */
    @Override
    protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
        requireCredentialAdmin();
    }

    @Description("@i18n:biz.get|根据id获取单条数据")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialOauthState get(@Name("id") String id,
                                       @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                       IServiceContext context) {
        requireCredentialAdmin();
        return super.get(id, ignoreUnknown, context);
    }

    @Description("@i18n:biz.batchGet|根据主键批量获取对象")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<NopCredentialOauthState> batchGet(@Name("ids") Collection<String> ids,
                                                  @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                                  IServiceContext context) {
        requireCredentialAdmin();
        return super.batchGet(ids, ignoreUnknown, context);
    }

    /**
     * 管理员判定：{@code nop.credential.admin-roles}（缺省 admin,nop-admin）。无登录态同样拒绝
     * ——state 绑定属引擎内部数据，查询面唯一合法消费方是管理员排障/审计。
     */
    private void requireCredentialAdmin() {
        IUserContext userContext = IUserContext.get();
        if (CredentialOwnership.isAdmin(userContext)) {
            return;
        }
        throw new NopException(CredentialErrors.ERR_CREDENTIAL_ADMIN_REQUIRED)
                .param(CredentialErrors.ARG_REQUIRED_ROLES,
                        CredentialOwnership.adminRolesAsString(CredentialOwnership.adminRoles()));
    }

    // ==================== 标准 mutation 旁路收口（引擎内部存储，GraphQL 面无任何合法写动作） ====================

    /**
     * 禁用标准 {@code save}：伪造 state 绑定行（引擎创建路径走 {@code NopCredentialOauthStateStore}）。
     */
    @Description("禁用标准 save（state 绑定行仅由 OAuth 引擎创建）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialOauthState save(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("standard save is disabled for credential oauth state: rows are created only by the OAuth engine (NopCredentialOauthStateStore)");
    }

    /**
     * 禁用标准 {@code update}：篡改绑定关系/consumed 标记（token 落点劫持、重放窗口恢复）。
     */
    @Description("禁用标准 update（state 绑定行不可变，一次性消费语义）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialOauthState update(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("standard update is disabled for credential oauth state: rows are immutable (single-consumption semantics, engine-managed)");
    }

    /**
     * 禁用标准 {@code delete}：删除在途 state 行构成授权流 DoS。
     */
    @Description("禁用标准 delete（在途 state 行删除即授权流 DoS）")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        throw new UnsupportedOperationException("standard delete is disabled for credential oauth state: deleting in-flight rows breaks ongoing authorizations (engine lazy-cleanup owns expiry)");
    }

    /**
     * 禁用标准 {@code batchDelete}：批量删除在途 state 行（DoS 旁路）。
     */
    @Description("禁用标准 batchDelete（批量授权流 DoS 旁路）")
    @BizMutation
    @Override
    public Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
        throw new UnsupportedOperationException("standard batchDelete is disabled for credential oauth state: engine-owned data has no GraphQL delete channel");
    }

    /**
     * 禁用 {@code updateByQuery}：按条件批量篡改绑定（token 落点劫持的主要载体）。
     */
    @Description("禁用 updateByQuery（按条件篡改 state 绑定）")
    @BizMutation
    @Override
    public int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data,
                             IServiceContext context) {
        throw new UnsupportedOperationException("updateByQuery is disabled for credential oauth state: it allows retargeting state bindings (token-capture vector)");
    }

    /**
     * 禁用 {@code deleteByQuery}：按条件批量清除在途 state 行（DoS 旁路）。
     */
    @Description("禁用 deleteByQuery（按条件清除在途 state 行）")
    @BizMutation
    @Override
    public int deleteByQuery(@Name("query") QueryBean query, IServiceContext context) {
        throw new UnsupportedOperationException("deleteByQuery is disabled for credential oauth state: it allows wiping in-flight authorizations (DoS vector)");
    }

    /**
     * 禁用 {@code copyForNew}：在引擎之外复制绑定行。
     */
    @Description("禁用 copyForNew（复制 state 绑定行绕过引擎创建路径）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialOauthState copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("copyForNew is disabled for credential oauth state: rows are created only by the OAuth engine");
    }
}
