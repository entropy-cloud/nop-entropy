
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynModule;

@BizModel("NopDynModule")
public class NopDynModuleBizModel extends CrudBizModel<NopDynModule>{
    public NopDynModuleBizModel(){
        setEntityName(NopDynModule.class.getName());
    }
}
