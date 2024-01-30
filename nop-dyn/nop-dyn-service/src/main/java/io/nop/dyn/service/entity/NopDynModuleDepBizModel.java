
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynModuleDep;

@BizModel("NopDynModuleDep")
public class NopDynModuleDepBizModel extends CrudBizModel<NopDynModuleDep>{
    public NopDynModuleDepBizModel(){
        setEntityName(NopDynModuleDep.class.getName());
    }
}
