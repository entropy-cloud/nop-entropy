
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthMfaCredentialBiz;
import io.nop.auth.dao.entity.NopAuthMfaCredential;

@BizModel("NopAuthMfaCredential")
public class NopAuthMfaCredentialBizModel extends CrudBizModel<NopAuthMfaCredential> implements INopAuthMfaCredentialBiz{
    public NopAuthMfaCredentialBizModel(){
        setEntityName(NopAuthMfaCredential.class.getName());
    }
}
