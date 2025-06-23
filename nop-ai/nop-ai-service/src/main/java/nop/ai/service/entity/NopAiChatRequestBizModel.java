
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiChatRequest;

@BizModel("NopAiChatRequest")
public class NopAiChatRequestBizModel extends CrudBizModel<NopAiChatRequest>{
    public NopAiChatRequestBizModel(){
        setEntityName(NopAiChatRequest.class.getName());
    }
}
