
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynPropMeta;

@BizModel("NopDynPropMeta")
public class NopDynPropMetaBizModel extends CrudBizModel<NopDynPropMeta>{
    public NopDynPropMetaBizModel(){
        setEntityName(NopDynPropMeta.class.getName());
    }
}
