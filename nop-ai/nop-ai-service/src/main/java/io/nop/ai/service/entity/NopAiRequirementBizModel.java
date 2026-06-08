
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiRequirementBiz;

import io.nop.ai.dao.entity.NopAiRequirement;

@BizModel("NopAiRequirement")
public class NopAiRequirementBizModel extends CrudBizModel<NopAiRequirement> implements INopAiRequirementBiz {
    public NopAiRequirementBizModel(){
        setEntityName(NopAiRequirement.class.getName());
    }
}
