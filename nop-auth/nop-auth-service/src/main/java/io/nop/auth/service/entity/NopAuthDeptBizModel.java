
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthDeptBiz;
import io.nop.auth.dao.entity.NopAuthDept;

@BizModel("NopAuthDept")
public class NopAuthDeptBizModel extends CrudBizModel<NopAuthDept> implements INopAuthDeptBiz{
    public NopAuthDeptBizModel(){
        setEntityName(NopAuthDept.class.getName());
    }
}
