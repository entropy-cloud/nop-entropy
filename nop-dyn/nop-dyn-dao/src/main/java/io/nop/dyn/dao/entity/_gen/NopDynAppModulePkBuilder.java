package io.nop.dyn.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.dyn.dao.entity.NopDynAppModule;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopDynAppModulePkBuilder{
    private Object[] values = new Object[2];

   
    public NopDynAppModulePkBuilder setAppId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopDynAppModulePkBuilder setModuleId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopDynAppModule.PK_PROP_NAMES,values);
    }
}
