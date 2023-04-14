
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.dao.entity.NopAuthRoleDataAuth;

@BizModel("NopAuthRoleDataAuth")
public class NopAuthRoleDataAuthBizModel extends CrudBizModel<NopAuthRoleDataAuth>{
    public NopAuthRoleDataAuthBizModel(){
        setEntityName(NopAuthRoleDataAuth.class.getName());
    }
}
