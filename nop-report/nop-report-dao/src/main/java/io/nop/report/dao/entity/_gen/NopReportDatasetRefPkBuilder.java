package io.nop.report.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.report.dao.entity.NopReportDatasetRef;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopReportDatasetRefPkBuilder{
    private Object[] values = new Object[2];

   
    public NopReportDatasetRefPkBuilder setRptId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopReportDatasetRefPkBuilder setDsId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopReportDatasetRef.PK_PROP_NAMES,values);
    }
}
