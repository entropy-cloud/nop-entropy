
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.ai.biz.INopAiEventBiz;
import io.nop.ai.dao.entity.NopAiEvent;

@BizModel("NopAiEvent")
public class NopAiEventBizModel extends CrudBizModel<NopAiEvent> implements INopAiEventBiz{
    public NopAiEventBizModel(){
        setEntityName(NopAiEvent.class.getName());
    }
}
