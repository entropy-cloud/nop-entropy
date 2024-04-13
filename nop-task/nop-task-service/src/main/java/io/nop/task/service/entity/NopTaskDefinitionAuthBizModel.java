
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.task.dao.entity.NopTaskDefinitionAuth;

@BizModel("NopTaskDefinitionAuth")
public class NopTaskDefinitionAuthBizModel extends CrudBizModel<NopTaskDefinitionAuth>{
    public NopTaskDefinitionAuthBizModel(){
        setEntityName(NopTaskDefinitionAuth.class.getName());
    }
}
