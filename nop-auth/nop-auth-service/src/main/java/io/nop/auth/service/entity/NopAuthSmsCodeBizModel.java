
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthSmsCodeBiz;
import io.nop.auth.dao.entity.NopAuthSmsCode;

@BizModel("NopAuthSmsCode")
public class NopAuthSmsCodeBizModel extends CrudBizModel<NopAuthSmsCode> implements INopAuthSmsCodeBiz{
    public NopAuthSmsCodeBizModel(){
        setEntityName(NopAuthSmsCode.class.getName());
    }
}
