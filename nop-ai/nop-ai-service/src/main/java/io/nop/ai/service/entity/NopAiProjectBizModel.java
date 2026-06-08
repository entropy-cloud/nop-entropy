
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiProjectBiz;

import io.nop.ai.dao.entity.NopAiProject;

@BizModel("NopAiProject")
public class NopAiProjectBizModel extends CrudBizModel<NopAiProject> implements INopAiProjectBiz {
    public NopAiProjectBizModel(){
        setEntityName(NopAiProject.class.getName());
    }
}
