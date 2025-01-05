
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysEvent;

@BizModel("NopSysEvent")
public class NopSysEventBizModel extends CrudBizModel<NopSysEvent>{
    public NopSysEventBizModel(){
        setEntityName(NopSysEvent.class.getName());
    }
}
