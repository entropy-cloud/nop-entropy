package io.nop.auth.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.auth.dao.entity.NopAuthGroupUser;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopAuthGroupUserPkBuilder{
    private Object[] values = new Object[2];

   
    public NopAuthGroupUserPkBuilder setUserId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopAuthGroupUserPkBuilder setGroupId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopAuthGroupUser.PK_PROP_NAMES,values);
    }
}
