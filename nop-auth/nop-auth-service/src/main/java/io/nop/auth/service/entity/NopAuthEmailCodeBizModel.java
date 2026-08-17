
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthEmailCodeBiz;
import io.nop.auth.dao.entity.NopAuthEmailCode;

@BizModel("NopAuthEmailCode")
public class NopAuthEmailCodeBizModel extends CrudBizModel<NopAuthEmailCode> implements INopAuthEmailCodeBiz{
    public NopAuthEmailCodeBizModel(){
        setEntityName(NopAuthEmailCode.class.getName());
    }
}
