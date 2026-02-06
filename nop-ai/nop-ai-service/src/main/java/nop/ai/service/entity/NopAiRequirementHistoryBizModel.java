
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import nop.ai.biz.INopAiRequirementHistoryBiz;

import nop.ai.dao.entity.NopAiRequirementHistory;

@BizModel("NopAiRequirementHistory")
public class NopAiRequirementHistoryBizModel extends CrudBizModel<NopAiRequirementHistory> implements INopAiRequirementHistoryBiz {
    public NopAiRequirementHistoryBizModel(){
        setEntityName(NopAiRequirementHistory.class.getName());
    }
}
