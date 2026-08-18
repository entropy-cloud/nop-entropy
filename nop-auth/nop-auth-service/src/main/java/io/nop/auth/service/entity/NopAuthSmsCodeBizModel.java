
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;

import io.nop.auth.biz.INopAuthSmsCodeBiz;
import io.nop.auth.dao.entity.NopAuthSmsCode;

/**
 * 同族瞬态码表纳入写路径收口（A2-followup-1 边界裁定：纳入）：短信码行仅由
 * {@code SmsCodeStore} 组件管理（send/verify/incrFailCount），无合法手工建行入口；通用
 * mutation 通道允许持权限者植入已知验证码后走正常验证流 = 第一因子旁路原语。继承
 * mutation 全部显式拒绝（{@link MfaSensitiveTableBizModel}）。
 */
@BizModel("NopAuthSmsCode")
public class NopAuthSmsCodeBizModel extends MfaSensitiveTableBizModel<NopAuthSmsCode> implements INopAuthSmsCodeBiz{
    public NopAuthSmsCodeBizModel(){
        setEntityName(NopAuthSmsCode.class.getName());
    }
}
