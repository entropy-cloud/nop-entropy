
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavChatSessionBiz;
import io.nop.datav.dao.entity.NopDatavChatSession;

@BizModel("NopDatavChatSession")
public class NopDatavChatSessionBizModel extends CrudBizModel<NopDatavChatSession> implements INopDatavChatSessionBiz{
    public NopDatavChatSessionBizModel(){
        setEntityName(NopDatavChatSession.class.getName());
    }
}
