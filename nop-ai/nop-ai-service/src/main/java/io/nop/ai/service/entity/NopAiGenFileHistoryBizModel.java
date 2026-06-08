
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiGenFileHistoryBiz;

import io.nop.ai.dao.entity.NopAiGenFileHistory;

@BizModel("NopAiGenFileHistory")
public class NopAiGenFileHistoryBizModel extends CrudBizModel<NopAiGenFileHistory> implements INopAiGenFileHistoryBiz {
    public NopAiGenFileHistoryBizModel(){
        setEntityName(NopAiGenFileHistory.class.getName());
    }
}
