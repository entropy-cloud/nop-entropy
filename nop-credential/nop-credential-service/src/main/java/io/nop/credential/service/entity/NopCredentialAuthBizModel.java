
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.mfa.MfaRequired;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.credential.biz.INopCredentialAuthBiz;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialAuth;
import io.nop.credential.service.CredentialOwnership;
import io.nop.dao.api.IEntityDao;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

/**
 * {@code NopCredentialAuth} 的授权管理面 BizModel（W11 Part B 裁定：独立 BizModel 载体，
 * 对齐 {@link NopCredentialUsageBizModel} 先例——实体自有查询面 + admin-only 双层，
 * 避免继续膨胀 {@code NopCredentialBizModel}）。
 *
 * <p><b>查询面限管理员</b>（双层防御：action-auth 资源收紧 admin + 本类运行时判定；
 * 无登录态同样拒绝——授权记录属管理信息，消费侧校验在 provider 明文出口不经本面）。
 *
 * <p><b>grant/revoke 幂等契约</b>（设计 §6.3，对齐 registerUsage/unregisterUsage 先例）：
 * grant 已存在的 (credentialId, roleId) = no-op 成功（唯一约束兜底并发竞态）；
 * revoke 不存在的记录 = no-op 成功（物理删除，无软删除列）。grant 拒绝 scope≠system
 * （user 级不叠加角色授权——owner 唯一明文出口，不产生永不生效的授权记录）；
 * roleId 仅非空校验（不做存在性校验——跨模块校验破坏 nop-credential 对 nop-auth 的
 * 依赖边界；死 roleId 求交永不命中，无害，管理面原样展示）。
 *
 * <p><b>标准 mutation 旁路收口</b>（对齐 Part A 六动作先例）：save/update/delete/
 * batchDelete/updateByQuery/deleteByQuery/copyForNew 全部禁用（抛
 * {@link UnsupportedOperationException}——绕过 grant/revoke 校验的旁路；delete 亦禁用：
 * revoke 为唯一删除通道、幂等契约所在）；batchGet 同 admin-only 语义（usage 先例，
 * 非行过滤）。容器接线：bean 条目由 codegen {@code _service.beans.xml} 自动生成，
 * 动作在 retention BizModel 上声明即被 GraphQL 发现。
 */
@BizModel("NopCredentialAuth")
public class NopCredentialAuthBizModel extends CrudBizModel<NopCredentialAuth> implements INopCredentialAuthBiz {
    public NopCredentialAuthBizModel(){
        setEntityName(NopCredentialAuth.class.getName());
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
    public NopCredentialAuth get(@Name("id") String id,
                                 @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                 IServiceContext context) {
        requireCredentialAdmin();
        return super.get(id, ignoreUnknown, context);
    }

    @Description("@i18n:biz.batchGet|根据主键批量获取对象")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<NopCredentialAuth> batchGet(@Name("ids") Collection<String> ids,
                                            @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                            IServiceContext context) {
        requireCredentialAdmin();
        return super.batchGet(ids, ignoreUnknown, context);
    }

    // ==================== grant / revoke（幂等契约） ====================

    /**
     * 授予角色取用某 system 级凭证的授权（use）。幂等：已存在 = no-op 成功（(credentialId, roleId)
     * 唯一约束兜底并发竞态，对齐 registerUsage 先例）。
     *
     * <p>入参校验：凭证存在且未删（NOT_FOUND/DELETED 归一，fail-closed）；scope≠system 拒绝
     * （user 级不叠加角色授权）；roleId 仅非空校验（字符串软引用 nop-auth 角色，不做跨模块
     * 存在性校验——依赖边界裁定，死 roleId 无害）。
     *
     * <p><b>C1b 操作级 MFA 标注</b>（A1-audit §二#4 缩窄裁定：权限变更——扩大凭证取用面）：
     * 生效前置 = 部署侧 {@code nop.auth.operation-mfa.enabled=true} + 用户 MFA 启用 +
     * checker SPI bean 装配（nop-auth-service）；三者任一不满足时行为与未标注一致。
     */
    @Description("授予凭证取用授权（幂等）")
    @BizMutation
    @MfaRequired
    public boolean grant(@Name("credentialId") String credentialId,
                         @Name("roleId") String roleId,
                         IServiceContext context) {
        requireCredentialAdmin();

        if (StringHelper.isEmpty(roleId)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_ROLE_ID_REQUIRED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }

        NopCredential credential = requireGrantableCredential(credentialId);

        IEntityDao<NopCredentialAuth> dao = dao();
        if (findGrants(credentialId, roleId).isEmpty()) {
            NopCredentialAuth auth = dao.newEntity();
            auth.setAuthId(StringHelper.generateUUID());
            auth.setCredentialId(credentialId);
            auth.setRoleId(roleId);
            auth.setCreateTime(new Timestamp(System.currentTimeMillis()));
            IUserContext userContext = IUserContext.get();
            auth.setCreatedBy(userContext != null && !StringHelper.isEmpty(userContext.getUserId())
                    ? userContext.getUserId() : credential.getCreatedBy());
            dao.saveEntityDirectly(auth);
        }
        return true; // 已存在 = no-op 成功
    }

    /**
     * 撤销角色的凭证取用授权。幂等：记录不存在 = no-op 成功。物理删除（无软删除列，
     * revoke→re-grant 循环靠物理删除与唯一约束天然成立）；revoke 为授权行唯一删除通道
     * （标准 delete 已禁用）。
     *
     * <p><b>C1b 操作级 MFA 标注</b>（A1-audit §二#4 缩窄裁定：权限变更——收缩凭证取用面，
     * 与 grant 对称拦截防劫持会话内权限操纵）：生效前置 = 部署侧
     * {@code nop.auth.operation-mfa.enabled=true} + 用户 MFA 启用 + checker SPI bean 装配
     * （nop-auth-service）；三者任一不满足时行为与未标注一致。
     */
    @Description("撤销凭证取用授权（幂等）")
    @BizMutation
    @MfaRequired
    public boolean revoke(@Name("credentialId") String credentialId,
                          @Name("roleId") String roleId,
                          IServiceContext context) {
        requireCredentialAdmin();

        if (StringHelper.isEmpty(roleId)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_ROLE_ID_REQUIRED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }

        IEntityDao<NopCredentialAuth> dao = dao();
        for (NopCredentialAuth auth : findGrants(credentialId, roleId)) {
            dao.deleteEntityDirectly(auth);
        }
        return true; // 不存在 = no-op 成功
    }

    /**
     * grant 目标凭证预检：存在（否则 NOT_FOUND 归一）、未删（DELETED 先序 fail-closed）、
     * system 级（user 级不叠加角色授权——owner 唯一明文出口，§5.3/§6.3）。
     */
    private NopCredential requireGrantableCredential(String credentialId) {
        IEntityDao<NopCredential> credentialDao = daoFor(NopCredential.class);
        NopCredential credential = credentialDao.getEntityById(credentialId);
        if (credential == null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }
        Byte delFlag = credential.getDelFlag();
        if (delFlag != null && delFlag != 0) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_DELETED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }
        if (CredentialOwnership.isUserScope(credential.getScope())) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_AUTH_NOT_SYSTEM_SCOPE)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId)
                    .param(CredentialErrors.ARG_SCOPE, credential.getScope())
                    .param(CredentialErrors.ARG_OWNER_ID, credential.getOwnerId());
        }
        return credential;
    }

    /**
     * 按 (credentialId, roleId) 查授权行（唯一键等值点查，至多一行）。
     */
    private List<NopCredentialAuth> findGrants(String credentialId, String roleId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopCredentialAuth.PROP_NAME_credentialId, credentialId));
        query.addFilter(FilterBeans.eq(NopCredentialAuth.PROP_NAME_roleId, roleId));
        return dao().findAllByQuery(query);
    }

    /**
     * 管理员判定：{@code nop.credential.admin-roles}（缺省 admin,nop-admin）。无登录态同样拒绝
     * ——授权管理面的唯一合法消费方是管理员（查询/收紧核对均为管理职责）。
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

    // ==================== 标准 mutation 旁路收口（对齐 Part A 六动作先例） ====================

    /**
     * 禁用标准 {@code save}：绕过 grant 的目标凭证预检（scope≠system 拒绝）与幂等契约；
     * 授权行只能经 {@link #grant} 创建。
     */
    @Description("禁用标准 save，请使用 grant")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialAuth save(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("use grant action instead: standard save bypasses the grant preconditions (scope/system-only, idempotent contract)");
    }

    /**
     * 禁用标准 {@code update}：授权行语义上不可变（grant = insert、revoke = 物理删除），
     * 无更新路径。
     */
    @Description("禁用标准 update，授权行不可变（grant/revoke 为唯一通道）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialAuth update(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("use grant/revoke actions instead: auth rows are immutable (no update path)");
    }

    /**
     * 禁用标准 {@code delete}：revoke 为授权行唯一删除通道（幂等契约所在）；
     * 凭证删除时的物理级联清理由 ORM cascadeDelete 承载，不经本动作。
     */
    @Description("禁用标准 delete，请使用 revoke")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        throw new UnsupportedOperationException("use revoke action instead: revoke is the only delete channel for auth rows (idempotent contract)");
    }

    /**
     * 禁用标准 {@code batchDelete}：绕过 revoke 的 admin 判定与幂等契约语义。
     */
    @Description("禁用标准 batchDelete，请逐条使用 revoke")
    @BizMutation
    @Override
    public Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
        throw new UnsupportedOperationException("standard batchDelete is disabled for credential auth: use revoke action per (credentialId, roleId)");
    }

    /**
     * 禁用 {@code updateByQuery}：绕过 admin 判定批量改授权行（授权行不可变，无更新路径）。
     */
    @Description("禁用 updateByQuery（授权行不可变）")
    @BizMutation
    @Override
    public int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data,
                             IServiceContext context) {
        throw new UnsupportedOperationException("updateByQuery is disabled for credential auth: auth rows are immutable (no update path)");
    }

    /**
     * 禁用 {@code deleteByQuery}：绕过 revoke 幂等契约与 admin 判定的批量删除旁路。
     */
    @Description("禁用 deleteByQuery（绕过 revoke 契约）")
    @BizMutation
    @Override
    public int deleteByQuery(@Name("query") QueryBean query, IServiceContext context) {
        throw new UnsupportedOperationException("deleteByQuery is disabled for credential auth: it bypasses the revoke contract");
    }

    /**
     * 禁用 {@code copyForNew}：在 grant 之外复制授权行（绕过目标凭证预检）。
     */
    @Description("禁用 copyForNew（在 grant 之外复制授权行）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialAuth copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("copyForNew is disabled for credential auth: it duplicates auth rows outside the grant action");
    }
}
