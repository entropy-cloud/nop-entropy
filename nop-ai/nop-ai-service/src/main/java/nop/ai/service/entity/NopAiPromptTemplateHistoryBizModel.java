
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiPromptTemplateHistory;

@BizModel("NopAiPromptTemplateHistory")
public class NopAiPromptTemplateHistoryBizModel extends CrudBizModel<NopAiPromptTemplateHistory>{
    public NopAiPromptTemplateHistoryBizModel(){
        setEntityName(NopAiPromptTemplateHistory.class.getName());
    }
}
