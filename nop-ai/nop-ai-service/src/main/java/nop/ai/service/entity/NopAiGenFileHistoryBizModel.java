
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import nop.ai.biz.INopAiGenFileHistoryBiz;

import nop.ai.dao.entity.NopAiGenFileHistory;

@BizModel("NopAiGenFileHistory")
public class NopAiGenFileHistoryBizModel extends CrudBizModel<NopAiGenFileHistory> implements INopAiGenFileHistoryBiz {
    public NopAiGenFileHistoryBizModel(){
        setEntityName(NopAiGenFileHistory.class.getName());
    }
}
