
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfDefinitionAuth;

@BizModel("NopWfDefinitionAuth")
public class NopWfDefinitionAuthBizModel extends CrudBizModel<NopWfDefinitionAuth>{
    public NopWfDefinitionAuthBizModel(){
        setEntityName(NopWfDefinitionAuth.class.getName());
    }
}
