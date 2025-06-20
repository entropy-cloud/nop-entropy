package io.nop.sys.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.sys.dao.entity.NopSysObjTag;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopSysObjTagPkBuilder{
    private Object[] values = new Object[3];

   
    public NopSysObjTagPkBuilder setBizObjId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopSysObjTagPkBuilder setBizObjName(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   
    public NopSysObjTagPkBuilder setTagId(java.lang.Long value){
        this.values[2] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopSysObjTag.PK_PROP_NAMES,values);
    }
}
