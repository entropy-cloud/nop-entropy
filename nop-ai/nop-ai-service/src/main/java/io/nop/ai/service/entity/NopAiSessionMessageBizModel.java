
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.ai.biz.INopAiSessionMessageBiz;
import io.nop.ai.dao.entity.NopAiSessionMessage;

@BizModel("NopAiSessionMessage")
public class NopAiSessionMessageBizModel extends CrudBizModel<NopAiSessionMessage> implements INopAiSessionMessageBiz{
    public NopAiSessionMessageBizModel(){
        setEntityName(NopAiSessionMessage.class.getName());
    }
}
