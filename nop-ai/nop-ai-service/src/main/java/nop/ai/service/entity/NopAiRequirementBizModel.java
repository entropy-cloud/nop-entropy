
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiRequirement;

@BizModel("NopAiRequirement")
public class NopAiRequirementBizModel extends CrudBizModel<NopAiRequirement>{
    public NopAiRequirementBizModel(){
        setEntityName(NopAiRequirement.class.getName());
    }
}
