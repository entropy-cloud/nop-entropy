
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import nop.ai.biz.INopAiPromptTemplateBiz;

import nop.ai.dao.entity.NopAiPromptTemplate;

@BizModel("NopAiPromptTemplate")
public class NopAiPromptTemplateBizModel extends CrudBizModel<NopAiPromptTemplate> implements INopAiPromptTemplateBiz {
    public NopAiPromptTemplateBizModel(){
        setEntityName(NopAiPromptTemplate.class.getName());
    }
}
