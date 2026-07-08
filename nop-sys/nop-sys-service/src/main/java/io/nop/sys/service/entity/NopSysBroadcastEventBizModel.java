
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.biz.INopSysBroadcastEventBiz;
import io.nop.sys.dao.entity.NopSysBroadcastEvent;

@BizModel("NopSysBroadcastEvent")
public class NopSysBroadcastEventBizModel extends CrudBizModel<NopSysBroadcastEvent> implements INopSysBroadcastEventBiz{
    public NopSysBroadcastEventBizModel(){
        setEntityName(NopSysBroadcastEvent.class.getName());
    }
}
