
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaBusinessDomainBiz;
import io.nop.metadata.dao.entity.NopMetaBusinessDomain;

@BizModel("NopMetaBusinessDomain")
public class NopMetaBusinessDomainBizModel extends CrudBizModel<NopMetaBusinessDomain> implements INopMetaBusinessDomainBiz{
    public NopMetaBusinessDomainBizModel(){
        setEntityName(NopMetaBusinessDomain.class.getName());
    }
}
