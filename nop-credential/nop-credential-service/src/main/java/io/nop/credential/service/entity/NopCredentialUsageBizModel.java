
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
import io.nop.credential.biz.INopCredentialUsageBiz;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.credential.service.CredentialOwnership;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

/**
 * {@code NopCredentialUsage} 的查询面 BizModel。
 *
 * <p><b>W11 查询面限管理员</b>（设计 §5.3）：usage 引用关系属管理信息，非管理员不可见
 * （双层防御：action-auth 资源收紧 admin + 本类运行时判定）。消费方登记/注销走 SPI
 * {@code ICredentialProvider.registerUsage}/{@code unregisterUsage}，不经本查询面，不受影响。
 *
 * <p><b>D4-06 mutation 面收口</b>（A1-audit successor，2026-08-17，对齐 D2-01 同型先例
 * {@link NopCredentialOauthStateBizModel}）：全部标准 mutation 禁用——usage 行由消费方经
 * SPI 登记/注销（(credentialId, consumerRef) 唯一约束幂等），GraphQL 面无任何合法写动作；
 * 尤其删除 usage 行可间接解锁被 {@code NopCredentialBizModel.delete} 引用计数拦截的凭证
 * 删除（绕过一期契约），必须堵死。
 */
@BizModel("NopCredentialUsage")
public class NopCredentialUsageBizModel extends CrudBizModel<NopCredentialUsage> implements INopCredentialUsageBiz {
    public NopCredentialUsageBizModel(){
        setEntityName(NopCredentialUsage.class.getName());
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
    public NopCredentialUsage get(@Name("id") String id,
                                  @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                  IServiceContext context) {
        requireCredentialAdmin();
        return super.get(id, ignoreUnknown, context);
    }

    @Description("@i18n:biz.batchGet|根据主键批量获取对象")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<NopCredentialUsage> batchGet(@Name("ids") Collection<String> ids,
                                             @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                             IServiceContext context) {
        requireCredentialAdmin();
        return super.batchGet(ids, ignoreUnknown, context);
    }

    /**
     * 管理员判定：{@code nop.credential.admin-roles}（缺省 admin,nop-admin）。无登录态同样拒绝
     * ——usage 查询面的唯一合法消费方是管理员管理面（消费方登记走 SPI），无内部调用场景。
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

    // ==================== 标准 mutation 旁路收口（D4-06：usage 行仅由消费方 SPI 维护） ====================

    /**
     * 禁用标准 {@code save}：伪造引用行（登记唯一合法通道 = 消费方 save/reconcile 经 SPI）。
     */
    @Description("禁用标准 save（usage 引用行仅由消费方经 ICredentialProvider.registerUsage 登记）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialUsage save(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("standard save is disabled for credential usage: rows are registered only by consumers via ICredentialProvider.registerUsage");
    }

    /**
     * 禁用标准 {@code update}：篡改引用关系（credentialId/consumerRef 指向）。
     */
    @Description("禁用标准 update（usage 引用关系不可经管理面篡改）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialUsage update(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("standard update is disabled for credential usage: reference bindings are consumer-owned (rebind goes through the consumer's save path)");
    }

    /**
     * 禁用标准 {@code delete}：删除 usage 行可间接解锁被引用计数拦截的凭证删除（绕过一期契约）；
     * 解绑唯一合法通道 = 消费方 {@code unregisterUsage}。
     */
    @Description("禁用标准 delete（删除 usage 行会绕过凭证删除的引用计数拦截）")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        throw new UnsupportedOperationException("standard delete is disabled for credential usage: deleting usage rows would bypass the credential-deletion reference-count interception (use the consumer's unregisterUsage)");
    }

    /**
     * 禁用标准 {@code batchDelete}：批量解锁凭证删除（同 delete 旁路）。
     */
    @Description("禁用标准 batchDelete（批量绕过引用计数拦截）")
    @BizMutation
    @Override
    public Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
        throw new UnsupportedOperationException("standard batchDelete is disabled for credential usage: it would bypass the credential-deletion reference-count interception");
    }

    /**
     * 禁用 {@code updateByQuery}：按条件批量篡改引用关系。
     */
    @Description("禁用 updateByQuery（按条件篡改 usage 引用关系）")
    @BizMutation
    @Override
    public int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data,
                             IServiceContext context) {
        throw new UnsupportedOperationException("updateByQuery is disabled for credential usage: reference bindings are consumer-owned");
    }

    /**
     * 禁用 {@code deleteByQuery}：按条件批量清除引用（解锁凭证删除的主要旁路载体）。
     */
    @Description("禁用 deleteByQuery（按条件清除 usage 引用绕过拦截）")
    @BizMutation
    @Override
    public int deleteByQuery(@Name("query") QueryBean query, IServiceContext context) {
        throw new UnsupportedOperationException("deleteByQuery is disabled for credential usage: it would wipe reference bindings and unlock blocked credential deletions");
    }

    /**
     * 禁用 {@code copyForNew}：在 SPI 之外复制引用行。
     */
    @Description("禁用 copyForNew（在消费方 SPI 之外复制引用行）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredentialUsage copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("copyForNew is disabled for credential usage: rows are registered only via ICredentialProvider.registerUsage");
    }
}
