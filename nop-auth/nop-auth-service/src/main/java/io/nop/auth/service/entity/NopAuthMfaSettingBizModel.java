
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;

import io.nop.auth.biz.INopAuthMfaSettingBiz;
import io.nop.auth.dao.entity.NopAuthMfaSetting;

/**
 * MFA 敏感表通用 CRUD 写路径收口（A2-followup-1，D5-F1）：继承 mutation 全部显式拒绝
 * （{@link MfaSensitiveTableBizModel}），写路径唯一入口 = bindMfa/confirmMfa/unbindMfa 族、
 * regenerateRecoveryCodes、resetUserMfa（{@code NopAuthUserBizModel} 专项动作）。
 */
@BizModel("NopAuthMfaSetting")
public class NopAuthMfaSettingBizModel extends MfaSensitiveTableBizModel<NopAuthMfaSetting> implements INopAuthMfaSettingBiz{
    public NopAuthMfaSettingBizModel(){
        setEntityName(NopAuthMfaSetting.class.getName());
    }
}
