
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.ai.biz.INopAiSessionContextBiz;
import io.nop.ai.dao.entity.NopAiSessionContext;

@BizModel("NopAiSessionContext")
public class NopAiSessionContextBizModel extends CrudBizModel<NopAiSessionContext> implements INopAiSessionContextBiz{
    public NopAiSessionContextBizModel(){
        setEntityName(NopAiSessionContext.class.getName());
    }
}
