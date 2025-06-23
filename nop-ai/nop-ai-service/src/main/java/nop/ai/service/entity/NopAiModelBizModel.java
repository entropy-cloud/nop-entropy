
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiModel;

@BizModel("NopAiModel")
public class NopAiModelBizModel extends CrudBizModel<NopAiModel>{
    public NopAiModelBizModel(){
        setEntityName(NopAiModel.class.getName());
    }
}
