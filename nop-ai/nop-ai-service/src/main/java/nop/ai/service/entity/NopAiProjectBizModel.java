
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiProject;

@BizModel("NopAiProject")
public class NopAiProjectBizModel extends CrudBizModel<NopAiProject>{
    public NopAiProjectBizModel(){
        setEntityName(NopAiProject.class.getName());
    }
}
