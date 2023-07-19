package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.sys.dao.entity._gen._NopSysLock;

import io.nop.sys.dao.entity._gen.NopSysLockPkBuilder;


@BizObjName("NopSysLock")
public class NopSysLock extends _NopSysLock{
    public NopSysLock(){
    }


    public static NopSysLockPkBuilder newPk(){
        return new NopSysLockPkBuilder();
    }

}
