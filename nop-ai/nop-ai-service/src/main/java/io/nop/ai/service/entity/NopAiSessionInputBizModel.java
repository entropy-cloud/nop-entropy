
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.ai.biz.INopAiSessionInputBiz;
import io.nop.ai.dao.entity.NopAiSessionInput;

@BizModel("NopAiSessionInput")
public class NopAiSessionInputBizModel extends CrudBizModel<NopAiSessionInput> implements INopAiSessionInputBiz{
    public NopAiSessionInputBizModel(){
        setEntityName(NopAiSessionInput.class.getName());
    }
}
