package io.nop.app._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.app.SimsExamExtField;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class SimsExamExtFieldPkBuilder{
    private Object[] values = new Object[2];

   
    public SimsExamExtFieldPkBuilder setExamId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public SimsExamExtFieldPkBuilder setFieldName(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(SimsExamExtField.PK_PROP_NAMES,values);
    }
}
