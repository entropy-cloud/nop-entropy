
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysEvent;
import io.nop.sys.biz.INopSysEventBiz;

@BizModel("NopSysEvent")
public class NopSysEventBizModel extends CrudBizModel<NopSysEvent> implements INopSysEventBiz {
    public NopSysEventBizModel(){
        setEntityName(NopSysEvent.class.getName());
    }
}
