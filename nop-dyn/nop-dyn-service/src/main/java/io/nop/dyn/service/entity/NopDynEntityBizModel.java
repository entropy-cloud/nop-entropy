
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynEntity;

@BizModel("NopDynEntity")
public class NopDynEntityBizModel extends CrudBizModel<NopDynEntity>{
    public NopDynEntityBizModel(){
        setEntityName(NopDynEntity.class.getName());
    }
}
