
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthRoleMfaPolicyBiz;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;

@BizModel("NopAuthRoleMfaPolicy")
public class NopAuthRoleMfaPolicyBizModel extends CrudBizModel<NopAuthRoleMfaPolicy> implements INopAuthRoleMfaPolicyBiz{
    public NopAuthRoleMfaPolicyBizModel(){
        setEntityName(NopAuthRoleMfaPolicy.class.getName());
    }
}
