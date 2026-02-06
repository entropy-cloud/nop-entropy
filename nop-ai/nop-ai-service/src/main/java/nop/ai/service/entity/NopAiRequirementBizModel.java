
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import nop.ai.biz.INopAiRequirementBiz;

import nop.ai.dao.entity.NopAiRequirement;

@BizModel("NopAiRequirement")
public class NopAiRequirementBizModel extends CrudBizModel<NopAiRequirement> implements INopAiRequirementBiz {
    public NopAiRequirementBizModel(){
        setEntityName(NopAiRequirement.class.getName());
    }
}
