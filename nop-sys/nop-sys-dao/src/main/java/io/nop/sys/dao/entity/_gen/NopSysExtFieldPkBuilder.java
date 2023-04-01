package io.nop.sys.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.sys.dao.entity.NopSysExtField;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopSysExtFieldPkBuilder{
    private Object[] values = new Object[3];

   
    public NopSysExtFieldPkBuilder setEntityName(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopSysExtFieldPkBuilder setEntityId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   
    public NopSysExtFieldPkBuilder setFieldName(java.lang.String value){
        this.values[2] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopSysExtField.PK_PROP_NAMES,values);
    }
}
