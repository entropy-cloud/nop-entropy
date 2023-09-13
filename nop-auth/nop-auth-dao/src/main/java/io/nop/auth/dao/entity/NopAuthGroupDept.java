package io.nop.auth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.auth.dao.entity._gen._NopAuthGroupDept;

import io.nop.auth.dao.entity._gen.NopAuthGroupDeptPkBuilder;


@BizObjName("NopAuthGroupDept")
public class NopAuthGroupDept extends _NopAuthGroupDept{
    public NopAuthGroupDept(){
    }


    public static NopAuthGroupDeptPkBuilder newPk(){
        return new NopAuthGroupDeptPkBuilder();
    }

}
