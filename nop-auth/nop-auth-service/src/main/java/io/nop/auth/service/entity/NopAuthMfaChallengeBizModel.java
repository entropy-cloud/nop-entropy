
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;

import io.nop.auth.biz.INopAuthMfaChallengeBiz;
import io.nop.auth.dao.entity.NopAuthMfaChallenge;

/**
 * 同族瞬态码表纳入写路径收口（A2-followup-1 边界裁定：纳入）：challenge 行仅由
 * {@code MfaChallengeStore} 组件管理（create/incrFailCount/consume/markVerified），无任何
 * 合法手工建行入口；通用 mutation 通道允许持权限者直接植入 challenge 行 = 第一因子旁路
 * 原语。继承 mutation 全部显式拒绝（{@link MfaSensitiveTableBizModel}）。
 */
@BizModel("NopAuthMfaChallenge")
public class NopAuthMfaChallengeBizModel extends MfaSensitiveTableBizModel<NopAuthMfaChallenge> implements INopAuthMfaChallengeBiz{
    public NopAuthMfaChallengeBizModel(){
        setEntityName(NopAuthMfaChallenge.class.getName());
    }
}
