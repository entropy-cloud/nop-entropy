
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynFunctionMeta;

@BizModel("NopDynFunctionMeta")
public class NopDynFunctionMetaBizModel extends CrudBizModel<NopDynFunctionMeta>{
    public NopDynFunctionMetaBizModel(){
        setEntityName(NopDynFunctionMeta.class.getName());
    }
}
