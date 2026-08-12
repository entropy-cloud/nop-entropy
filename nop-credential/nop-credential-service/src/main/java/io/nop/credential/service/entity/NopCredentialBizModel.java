
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.credential.biz.INopCredentialBiz;
import io.nop.credential.dao.entity.NopCredential;

@BizModel("NopCredential")
public class NopCredentialBizModel extends CrudBizModel<NopCredential> implements INopCredentialBiz{
    public NopCredentialBizModel(){
        setEntityName(NopCredential.class.getName());
    }
}
