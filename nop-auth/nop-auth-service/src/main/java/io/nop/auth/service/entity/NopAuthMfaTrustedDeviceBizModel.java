
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.biz.INopAuthMfaTrustedDeviceBiz;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.core.context.IServiceContext;
import jakarta.inject.Inject;

import java.util.Set;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN;
import static io.nop.auth.service.NopAuthErrors.ARG_USER_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;

/**
 * MFA 敏感表通用 CRUD 写路径收口（A2-followup-1，D6-1）：可信设备行的登记不变式（指纹三
 * 输入 SHA-256、固定 30d 窗口、满员判定、五审计事件）全部内聚在
 * {@link MfaTrustedDeviceManager}——通用 save/update 通道允许伪造任意 userId 的离线可算
 * deviceHash + 任意 expireAt，注入登录级 MFA 豁免，已全部显式拒绝
 * （{@link MfaSensitiveTableBizModel}）。
 *
 * <p><b>管理端 carve-out</b>：{@code delete} 保留为唯一管理端 mutation——运行时 admin 校验
 * （{@code NopAuthRoleBizModel.requireAdmin} 先例）+ 经 manager 物理删除 + revoke 族审计
 * 事件（reason=admin-removed）。自助移除继续走
 * {@code NopAuthUserBizModel.removeTrustedDevice}（本人限定），不受影响。
 */
@BizModel("NopAuthMfaTrustedDevice")
public class NopAuthMfaTrustedDeviceBizModel extends MfaSensitiveTableBizModel<NopAuthMfaTrustedDevice>
        implements INopAuthMfaTrustedDeviceBiz {

    @Inject
    protected MfaTrustedDeviceManager trustedDeviceManager;

    public NopAuthMfaTrustedDeviceBizModel() {
        setEntityName(NopAuthMfaTrustedDevice.class.getName());
    }

    /**
     * 管理端删除（carve-out）：admin 校验 + manager 物理删除 + 审计。行不存在返回 false
     * （与基类 delete 的归一语义一致，不暴露存在性差异）。
     */
    @Description("Delete a trusted device (admin only, audited physical delete)")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        requireAdmin(context);
        return trustedDeviceManager.removeBySidForAdmin(id);
    }

    /** 运行时 admin 角色校验（{@code NopAuthRoleBizModel.requireAdmin} 先例模式）。 */
    private void requireAdmin(IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null) {
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);
        }
        Set<String> roles = userContext.getRoles();
        if (roles == null || (!roles.contains(NopAuthConstants.ROLE_ADMIN)
                && !roles.contains(NopAuthConstants.ROLE_NOP_ADMIN))) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userContext.getUserId())
                    .param("msg", "only admin can remove trusted devices");
        }
    }
}
