
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.ai.biz.INopAiSessionBiz;
import io.nop.ai.dao.entity.NopAiSession;

@BizModel("NopAiSession")
public class NopAiSessionBizModel extends CrudBizModel<NopAiSession> implements INopAiSessionBiz{
    public NopAiSessionBizModel(){
        setEntityName(NopAiSession.class.getName());
    }
}
