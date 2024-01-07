package io.nop.auth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.auth.dao.entity._gen._NopAuthGroupUser;

import io.nop.auth.dao.entity._gen.NopAuthGroupUserPkBuilder;


@BizObjName("NopAuthGroupUser")
public class NopAuthGroupUser extends _NopAuthGroupUser{


    public static NopAuthGroupUserPkBuilder newPk(){
        return new NopAuthGroupUserPkBuilder();
    }

}
