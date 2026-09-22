
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthLoginAttemptBiz;
import io.nop.auth.dao.entity.NopAuthLoginAttempt;

@BizModel("NopAuthLoginAttempt")
public class NopAuthLoginAttemptBizModel extends CrudBizModel<NopAuthLoginAttempt> implements INopAuthLoginAttemptBiz{
    public NopAuthLoginAttemptBizModel(){
        setEntityName(NopAuthLoginAttempt.class.getName());
    }
}
