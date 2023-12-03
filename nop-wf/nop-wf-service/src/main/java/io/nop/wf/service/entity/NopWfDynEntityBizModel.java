
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfDynEntity;

@BizModel("NopWfDynEntity")
public class NopWfDynEntityBizModel extends CrudBizModel<NopWfDynEntity>{
    public NopWfDynEntityBizModel(){
        setEntityName(NopWfDynEntity.class.getName());
    }
}
