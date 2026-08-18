
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;

import io.nop.auth.biz.INopAuthMfaRecoveryCodeBiz;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;

/**
 * MFA 敏感表通用 CRUD 写路径收口（A2-followup-1，D3-F3）：继承 mutation 全部显式拒绝
 * （{@link MfaSensitiveTableBizModel}）——关闭"植入自算恢复码/复活已用码"路径。恢复码行
 * 唯一写入口 = regenerateRecoveryCodes（confirmMfa 成功/generateRecoveryCodes）与
 * verifyRecoveryCode 的条件写置 used（D3-F2）。
 */
@BizModel("NopAuthMfaRecoveryCode")
public class NopAuthMfaRecoveryCodeBizModel extends MfaSensitiveTableBizModel<NopAuthMfaRecoveryCode> implements INopAuthMfaRecoveryCodeBiz{
    public NopAuthMfaRecoveryCodeBizModel(){
        setEntityName(NopAuthMfaRecoveryCode.class.getName());
    }
}
