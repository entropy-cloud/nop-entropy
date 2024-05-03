
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.dao.entity.NopJobDefinition;

@BizModel("NopJobDefinition")
public class NopJobDefinitionBizModel extends CrudBizModel<NopJobDefinition>{
    public NopJobDefinitionBizModel(){
        setEntityName(NopJobDefinition.class.getName());
    }
}
