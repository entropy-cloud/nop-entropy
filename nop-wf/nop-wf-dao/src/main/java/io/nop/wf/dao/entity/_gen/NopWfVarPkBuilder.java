package io.nop.wf.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.wf.dao.entity.NopWfVar;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopWfVarPkBuilder{
    private Object[] values = new Object[2];

   
    public NopWfVarPkBuilder setWfId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopWfVarPkBuilder setFieldName(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopWfVar.PK_PROP_NAMES,values);
    }
}
