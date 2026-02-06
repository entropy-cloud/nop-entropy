
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.task.dao.entity.NopTaskDefinitionAuth;
import io.nop.task.biz.INopTaskDefinitionAuthBiz;

@BizModel("NopTaskDefinitionAuth")
public class NopTaskDefinitionAuthBizModel extends CrudBizModel<NopTaskDefinitionAuth> implements INopTaskDefinitionAuthBiz {
    public NopTaskDefinitionAuthBizModel(){
        setEntityName(NopTaskDefinitionAuth.class.getName());
    }
}
