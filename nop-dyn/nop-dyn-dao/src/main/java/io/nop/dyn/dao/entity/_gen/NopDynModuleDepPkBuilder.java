package io.nop.dyn.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.dyn.dao.entity.NopDynModuleDep;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopDynModuleDepPkBuilder{
    private Object[] values = new Object[2];

   
    public NopDynModuleDepPkBuilder setModuleId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopDynModuleDepPkBuilder setDepModuleId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopDynModuleDep.PK_PROP_NAMES,values);
    }
}
