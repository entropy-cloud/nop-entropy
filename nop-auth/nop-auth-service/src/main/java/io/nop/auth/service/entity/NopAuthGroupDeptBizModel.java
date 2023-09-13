
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.dao.entity.NopAuthGroupDept;

@BizModel("NopAuthGroupDept")
public class NopAuthGroupDeptBizModel extends CrudBizModel<NopAuthGroupDept>{
    public NopAuthGroupDeptBizModel(){
        setEntityName(NopAuthGroupDept.class.getName());
    }
}
