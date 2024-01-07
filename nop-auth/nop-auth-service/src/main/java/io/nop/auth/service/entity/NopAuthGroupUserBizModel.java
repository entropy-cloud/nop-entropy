
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.dao.entity.NopAuthGroupUser;

@BizModel("NopAuthGroupUser")
public class NopAuthGroupUserBizModel extends CrudBizModel<NopAuthGroupUser>{
    public NopAuthGroupUserBizModel(){
        setEntityName(NopAuthGroupUser.class.getName());
    }
}
