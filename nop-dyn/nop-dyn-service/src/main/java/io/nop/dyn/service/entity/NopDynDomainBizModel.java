
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynDomain;

@BizModel("NopDynDomain")
public class NopDynDomainBizModel extends CrudBizModel<NopDynDomain>{
    public NopDynDomainBizModel(){
        setEntityName(NopDynDomain.class.getName());
    }
}
