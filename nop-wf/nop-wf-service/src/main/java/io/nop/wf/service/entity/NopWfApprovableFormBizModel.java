
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.biz.INopWfApprovableFormBiz;
import io.nop.wf.dao.entity.NopWfApprovableForm;

@BizModel("NopWfApprovableForm")
public class NopWfApprovableFormBizModel extends CrudBizModel<NopWfApprovableForm> implements INopWfApprovableFormBiz{
    public NopWfApprovableFormBizModel(){
        setEntityName(NopWfApprovableForm.class.getName());
    }
}
