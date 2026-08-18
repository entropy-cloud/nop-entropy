
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;

import io.nop.auth.biz.INopAuthRoleMfaPolicyBiz;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;

/**
 * MFA 敏感表通用 CRUD 写路径收口（A2-followup-1，D5-F1）：继承 mutation 全部显式拒绝
 * （{@link MfaSensitiveTableBizModel}）。策略行唯一写入口 = {@code NopAuthRoleBizModel
 * .saveMfaPolicy/removeMfaPolicy}（requireAdmin + minMfaLevel 1/3 校验 + 审计，零改动）。
 */
@BizModel("NopAuthRoleMfaPolicy")
public class NopAuthRoleMfaPolicyBizModel extends MfaSensitiveTableBizModel<NopAuthRoleMfaPolicy> implements INopAuthRoleMfaPolicyBiz{
    public NopAuthRoleMfaPolicyBizModel(){
        setEntityName(NopAuthRoleMfaPolicy.class.getName());
    }
}
