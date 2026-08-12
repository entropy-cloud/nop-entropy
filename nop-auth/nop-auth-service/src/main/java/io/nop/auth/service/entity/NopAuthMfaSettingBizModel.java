
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthMfaSettingBiz;
import io.nop.auth.dao.entity.NopAuthMfaSetting;

@BizModel("NopAuthMfaSetting")
public class NopAuthMfaSettingBizModel extends CrudBizModel<NopAuthMfaSetting> implements INopAuthMfaSettingBiz{
    public NopAuthMfaSettingBizModel(){
        setEntityName(NopAuthMfaSetting.class.getName());
    }
}
