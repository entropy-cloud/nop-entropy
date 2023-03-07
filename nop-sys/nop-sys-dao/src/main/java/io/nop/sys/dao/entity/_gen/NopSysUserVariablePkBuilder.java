package io.nop.sys.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.sys.dao.entity.NopSysUserVariable;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopSysUserVariablePkBuilder{
    private Object[] values = new Object[2];

   
    public NopSysUserVariablePkBuilder setUserId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopSysUserVariablePkBuilder setVarName(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopSysUserVariable.PK_PROP_NAMES,values);
    }
}
