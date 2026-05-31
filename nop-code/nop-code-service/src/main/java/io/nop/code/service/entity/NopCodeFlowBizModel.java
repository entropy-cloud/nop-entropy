package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeFlowBiz;
import io.nop.code.dao.entity.NopCodeFlow;
@BizModel("NopCodeFlow")
public class NopCodeFlowBizModel extends CrudBizModel<NopCodeFlow> implements INopCodeFlowBiz{
    public NopCodeFlowBizModel(){
        setEntityName(NopCodeFlow.class.getName());
    }
}
