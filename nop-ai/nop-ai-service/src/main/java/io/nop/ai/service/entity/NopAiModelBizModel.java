
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiModelBiz;

import io.nop.ai.dao.entity.NopAiModel;

@BizModel("NopAiModel")
public class NopAiModelBizModel extends CrudBizModel<NopAiModel> implements INopAiModelBiz {
    public NopAiModelBizModel(){
        setEntityName(NopAiModel.class.getName());
    }
}
