package io.nop.batch.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.batch.dao.entity.NopBatchTaskState;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopBatchTaskStatePkBuilder{
    private Object[] values = new Object[2];

   
    public NopBatchTaskStatePkBuilder setTaskId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopBatchTaskStatePkBuilder setFieldName(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopBatchTaskState.PK_PROP_NAMES,values);
    }
}
