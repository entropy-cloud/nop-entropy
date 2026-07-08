
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.biz.INopSysBroadcastCursorBiz;
import io.nop.sys.dao.entity.NopSysBroadcastCursor;

@BizModel("NopSysBroadcastCursor")
public class NopSysBroadcastCursorBizModel extends CrudBizModel<NopSysBroadcastCursor> implements INopSysBroadcastCursorBiz{
    public NopSysBroadcastCursorBizModel(){
        setEntityName(NopSysBroadcastCursor.class.getName());
    }
}
