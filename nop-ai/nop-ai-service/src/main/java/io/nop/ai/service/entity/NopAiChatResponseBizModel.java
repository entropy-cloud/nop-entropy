
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiChatResponseBiz;

import io.nop.ai.dao.entity.NopAiChatResponse;

@BizModel("NopAiChatResponse")
public class NopAiChatResponseBizModel extends CrudBizModel<NopAiChatResponse> implements INopAiChatResponseBiz {
    public NopAiChatResponseBizModel(){
        setEntityName(NopAiChatResponse.class.getName());
    }
}
