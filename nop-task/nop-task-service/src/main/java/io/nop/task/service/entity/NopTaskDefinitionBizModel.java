
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.task.dao.entity.NopTaskDefinition;
import io.nop.task.biz.INopTaskDefinitionBiz;

@BizModel("NopTaskDefinition")
public class NopTaskDefinitionBizModel extends CrudBizModel<NopTaskDefinition> implements INopTaskDefinitionBiz {
    public NopTaskDefinitionBizModel(){
        setEntityName(NopTaskDefinition.class.getName());
    }
}
