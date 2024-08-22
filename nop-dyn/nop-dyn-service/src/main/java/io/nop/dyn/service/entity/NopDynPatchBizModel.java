
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynPatch;

@BizModel("NopDynPatch")
public class NopDynPatchBizModel extends CrudBizModel<NopDynPatch>{
    public NopDynPatchBizModel(){
        setEntityName(NopDynPatch.class.getName());
    }
}
