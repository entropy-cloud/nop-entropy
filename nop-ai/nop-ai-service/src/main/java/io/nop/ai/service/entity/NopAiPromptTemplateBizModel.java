
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiPromptTemplateBiz;

import io.nop.ai.dao.entity.NopAiPromptTemplate;

@BizModel("NopAiPromptTemplate")
public class NopAiPromptTemplateBizModel extends CrudBizModel<NopAiPromptTemplate> implements INopAiPromptTemplateBiz {
    public NopAiPromptTemplateBizModel(){
        setEntityName(NopAiPromptTemplate.class.getName());
    }
}
