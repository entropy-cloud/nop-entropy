
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
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

import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

/**
 * {@code NopCredentialUsage} 的查询面 BizModel。
 *
 * <p><b>W11 查询面限管理员</b>（设计 §5.3）：usage 引用关系属管理信息，非管理员不可见
 * （双层防御：action-auth 资源收紧 admin + 本类运行时判定）。消费方登记/注销走 SPI
 * {@code ICredentialProvider.registerUsage}/{@code unregisterUsage}，不经本查询面，不受影响。
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
}
