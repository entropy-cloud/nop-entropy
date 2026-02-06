
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.dao.entity.NopJobDefinition;
import io.nop.job.biz.INopJobDefinitionBiz;

@BizModel("NopJobDefinition")
public class NopJobDefinitionBizModel extends CrudBizModel<NopJobDefinition> implements INopJobDefinitionBiz {
    public NopJobDefinitionBizModel(){
        setEntityName(NopJobDefinition.class.getName());
    }
}
