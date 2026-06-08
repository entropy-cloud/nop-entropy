
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiPromptTemplateHistoryBiz;

import io.nop.ai.dao.entity.NopAiPromptTemplateHistory;

@BizModel("NopAiPromptTemplateHistory")
public class NopAiPromptTemplateHistoryBizModel extends CrudBizModel<NopAiPromptTemplateHistory> implements INopAiPromptTemplateHistoryBiz {
    public NopAiPromptTemplateHistoryBizModel(){
        setEntityName(NopAiPromptTemplateHistory.class.getName());
    }
}
