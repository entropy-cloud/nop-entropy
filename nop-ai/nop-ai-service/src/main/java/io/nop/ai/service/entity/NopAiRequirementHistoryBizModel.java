
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiRequirementHistoryBiz;

import io.nop.ai.dao.entity.NopAiRequirementHistory;

@BizModel("NopAiRequirementHistory")
public class NopAiRequirementHistoryBizModel extends CrudBizModel<NopAiRequirementHistory> implements INopAiRequirementHistoryBiz {
    public NopAiRequirementHistoryBizModel(){
        setEntityName(NopAiRequirementHistory.class.getName());
    }
}
