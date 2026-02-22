
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeCallBiz;
import io.nop.code.dao.entity.NopCodeCall;

@BizModel("NopCodeCall")
public class NopCodeCallBizModel extends CrudBizModel<NopCodeCall> implements INopCodeCallBiz{
    public NopCodeCallBizModel(){
        setEntityName(NopCodeCall.class.getName());
    }
}
