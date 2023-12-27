
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynAppModule;

@BizModel("NopDynAppModule")
public class NopDynAppModuleBizModel extends CrudBizModel<NopDynAppModule>{
    public NopDynAppModuleBizModel(){
        setEntityName(NopDynAppModule.class.getName());
    }
}
