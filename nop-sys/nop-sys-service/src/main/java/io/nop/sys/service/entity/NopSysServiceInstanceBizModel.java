
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysServiceInstance;
import io.nop.sys.biz.INopSysServiceInstanceBiz;

@BizModel("NopSysServiceInstance")
public class NopSysServiceInstanceBizModel extends CrudBizModel<NopSysServiceInstance> implements INopSysServiceInstanceBiz {
    public NopSysServiceInstanceBizModel(){
        setEntityName(NopSysServiceInstance.class.getName());
    }
}
