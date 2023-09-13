
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.dao.entity.NopAuthGroup;

@BizModel("NopAuthGroup")
public class NopAuthGroupBizModel extends CrudBizModel<NopAuthGroup>{
    public NopAuthGroupBizModel(){
        setEntityName(NopAuthGroup.class.getName());
    }
}
