package io.nop.auth.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.auth.dao.entity.NopAuthExtLogin;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopAuthExtLoginPkBuilder{
    private Object[] values = new Object[2];

   
    public NopAuthExtLoginPkBuilder setUserId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopAuthExtLoginPkBuilder setLoginType(java.lang.Integer value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopAuthExtLogin.PK_PROP_NAMES,values);
    }
}
