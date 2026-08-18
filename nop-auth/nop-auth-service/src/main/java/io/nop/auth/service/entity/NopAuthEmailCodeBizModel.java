
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;

import io.nop.auth.biz.INopAuthEmailCodeBiz;
import io.nop.auth.dao.entity.NopAuthEmailCode;

/**
 * 同族瞬态码表纳入写路径收口（A2-followup-1 边界裁定：纳入）：邮件码行仅由
 * {@code EmailCodeStore} 组件管理（send/verify/incrFailCount），无合法手工建行入口；通用
 * mutation 通道允许持权限者植入已知验证码后走正常验证流 = 第一因子旁路原语。继承
 * mutation 全部显式拒绝（{@link MfaSensitiveTableBizModel}）。
 */
@BizModel("NopAuthEmailCode")
public class NopAuthEmailCodeBizModel extends MfaSensitiveTableBizModel<NopAuthEmailCode> implements INopAuthEmailCodeBiz{
    public NopAuthEmailCodeBizModel(){
        setEntityName(NopAuthEmailCode.class.getName());
    }
}
