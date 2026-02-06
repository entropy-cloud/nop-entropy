
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import nop.ai.biz.INopAiKnowledgeBiz;

import nop.ai.dao.entity.NopAiKnowledge;

@BizModel("NopAiKnowledge")
public class NopAiKnowledgeBizModel extends CrudBizModel<NopAiKnowledge> implements INopAiKnowledgeBiz {
    public NopAiKnowledgeBizModel(){
        setEntityName(NopAiKnowledge.class.getName());
    }
}
