
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaDomainBiz;
import io.nop.metadata.dao.entity.NopMetaDomain;

@BizModel("NopMetaDomain")
public class NopMetaDomainBizModel extends CrudBizModel<NopMetaDomain> implements INopMetaDomainBiz{
    public NopMetaDomainBizModel(){
        setEntityName(NopMetaDomain.class.getName());
    }
}
