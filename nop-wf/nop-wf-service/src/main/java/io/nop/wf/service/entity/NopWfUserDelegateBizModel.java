
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfUserDelegate;

@BizModel("NopWfUserDelegate")
public class NopWfUserDelegateBizModel extends CrudBizModel<NopWfUserDelegate>{
    public NopWfUserDelegateBizModel(){
        setEntityName(NopWfUserDelegate.class.getName());
    }
}
