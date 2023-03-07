package io.nop.wf.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.wf.dao.entity.NopWfOutput;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopWfOutputPkBuilder{
    private Object[] values = new Object[2];

   
    public NopWfOutputPkBuilder setWfId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopWfOutputPkBuilder setFieldName(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopWfOutput.PK_PROP_NAMES,values);
    }
}
