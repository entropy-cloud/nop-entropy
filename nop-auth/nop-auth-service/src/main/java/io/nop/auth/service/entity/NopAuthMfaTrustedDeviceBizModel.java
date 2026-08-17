
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthMfaTrustedDeviceBiz;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;

@BizModel("NopAuthMfaTrustedDevice")
public class NopAuthMfaTrustedDeviceBizModel extends CrudBizModel<NopAuthMfaTrustedDevice> implements INopAuthMfaTrustedDeviceBiz{
    public NopAuthMfaTrustedDeviceBizModel(){
        setEntityName(NopAuthMfaTrustedDevice.class.getName());
    }
}
