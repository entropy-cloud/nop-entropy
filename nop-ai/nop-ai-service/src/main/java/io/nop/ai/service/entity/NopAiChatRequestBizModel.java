
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiChatRequestBiz;

import io.nop.ai.dao.entity.NopAiChatRequest;

@BizModel("NopAiChatRequest")
public class NopAiChatRequestBizModel extends CrudBizModel<NopAiChatRequest> implements INopAiChatRequestBiz {
    public NopAiChatRequestBizModel(){
        setEntityName(NopAiChatRequest.class.getName());
    }
}
