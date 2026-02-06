
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import nop.ai.biz.INopAiProjectConfigBiz;

import nop.ai.dao.entity.NopAiProjectConfig;

@BizModel("NopAiProjectConfig")
public class NopAiProjectConfigBizModel extends CrudBizModel<NopAiProjectConfig> implements INopAiProjectConfigBiz {
    public NopAiProjectConfigBizModel(){
        setEntityName(NopAiProjectConfig.class.getName());
    }
}
