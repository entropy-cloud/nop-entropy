
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthMfaRecoveryCodeBiz;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;

@BizModel("NopAuthMfaRecoveryCode")
public class NopAuthMfaRecoveryCodeBizModel extends CrudBizModel<NopAuthMfaRecoveryCode> implements INopAuthMfaRecoveryCodeBiz{
    public NopAuthMfaRecoveryCodeBizModel(){
        setEntityName(NopAuthMfaRecoveryCode.class.getName());
    }
}
