
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiProjectConfigBiz;

import io.nop.ai.dao.entity.NopAiProjectConfig;

@BizModel("NopAiProjectConfig")
public class NopAiProjectConfigBizModel extends CrudBizModel<NopAiProjectConfig> implements INopAiProjectConfigBiz {
    public NopAiProjectConfigBizModel(){
        setEntityName(NopAiProjectConfig.class.getName());
    }
}
