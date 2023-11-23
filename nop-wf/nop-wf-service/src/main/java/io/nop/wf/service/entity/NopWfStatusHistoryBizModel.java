
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfStatusHistory;

@BizModel("NopWfStatusHistory")
public class NopWfStatusHistoryBizModel extends CrudBizModel<NopWfStatusHistory>{
    public NopWfStatusHistoryBizModel(){
        setEntityName(NopWfStatusHistory.class.getName());
    }
}
