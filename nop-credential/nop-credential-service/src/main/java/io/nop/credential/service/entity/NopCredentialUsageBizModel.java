
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.credential.biz.INopCredentialUsageBiz;
import io.nop.credential.dao.entity.NopCredentialUsage;

@BizModel("NopCredentialUsage")
public class NopCredentialUsageBizModel extends CrudBizModel<NopCredentialUsage> implements INopCredentialUsageBiz{
    public NopCredentialUsageBizModel(){
        setEntityName(NopCredentialUsage.class.getName());
    }
}
