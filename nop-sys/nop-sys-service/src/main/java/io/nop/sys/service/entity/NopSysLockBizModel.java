
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysLock;

@BizModel("NopSysLock")
public class NopSysLockBizModel extends CrudBizModel<NopSysLock>{
    public NopSysLockBizModel(){
        setEntityName(NopSysLock.class.getName());
    }
}
