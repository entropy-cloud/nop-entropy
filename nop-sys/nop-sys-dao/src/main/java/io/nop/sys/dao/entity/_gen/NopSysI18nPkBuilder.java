package io.nop.sys.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.sys.dao.entity.NopSysI18n;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopSysI18nPkBuilder{
    private Object[] values = new Object[2];

   
    public NopSysI18nPkBuilder setI18nKey(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopSysI18nPkBuilder setI18nLocale(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopSysI18n.PK_PROP_NAMES,values);
    }
}
