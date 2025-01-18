
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysServiceInstance;

@BizModel("NopSysServiceInstance")
public class NopSysServiceInstanceBizModel extends CrudBizModel<NopSysServiceInstance>{
    public NopSysServiceInstanceBizModel(){
        setEntityName(NopSysServiceInstance.class.getName());
    }
}
