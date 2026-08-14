
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavChatMessageBiz;
import io.nop.datav.dao.entity.NopDatavChatMessage;

@BizModel("NopDatavChatMessage")
public class NopDatavChatMessageBizModel extends CrudBizModel<NopDatavChatMessage> implements INopDatavChatMessageBiz{
    public NopDatavChatMessageBizModel(){
        setEntityName(NopDatavChatMessage.class.getName());
    }
}
