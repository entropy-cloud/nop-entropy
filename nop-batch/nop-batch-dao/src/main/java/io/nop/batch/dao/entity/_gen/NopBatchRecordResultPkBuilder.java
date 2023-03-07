package io.nop.batch.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.batch.dao.entity.NopBatchRecordResult;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopBatchRecordResultPkBuilder{
    private Object[] values = new Object[2];

   
    public NopBatchRecordResultPkBuilder setTaskId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopBatchRecordResultPkBuilder setRecordKey(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopBatchRecordResult.PK_PROP_NAMES,values);
    }
}
