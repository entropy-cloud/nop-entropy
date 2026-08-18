
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;

import io.nop.auth.biz.INopAuthMfaCredentialBiz;
import io.nop.auth.dao.entity.NopAuthMfaCredential;

/**
 * MFA 敏感表通用 CRUD 写路径收口（A2-followup-1，D5-F1）：继承 mutation 全部显式拒绝
 * （{@link MfaSensitiveTableBizModel}）。WebAuthn 凭证行写路径唯一入口 = 注册/解绑/移除
 * ceremony（{@code NopAuthUserBizModel}）与因子失效边界物理删除（unbindMfa/resetUserMfa/
 * 恢复码使用——last-credential 守卫在专项路径内）。
 */
@BizModel("NopAuthMfaCredential")
public class NopAuthMfaCredentialBizModel extends MfaSensitiveTableBizModel<NopAuthMfaCredential> implements INopAuthMfaCredentialBiz{
    public NopAuthMfaCredentialBizModel(){
        setEntityName(NopAuthMfaCredential.class.getName());
    }
}
