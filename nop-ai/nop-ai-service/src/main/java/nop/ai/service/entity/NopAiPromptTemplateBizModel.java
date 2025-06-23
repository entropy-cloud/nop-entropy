
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiPromptTemplate;

@BizModel("NopAiPromptTemplate")
public class NopAiPromptTemplateBizModel extends CrudBizModel<NopAiPromptTemplate>{
    public NopAiPromptTemplateBizModel(){
        setEntityName(NopAiPromptTemplate.class.getName());
    }
}
