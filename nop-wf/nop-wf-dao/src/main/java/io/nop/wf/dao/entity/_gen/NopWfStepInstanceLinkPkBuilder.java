package io.nop.wf.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.wf.dao.entity.NopWfStepInstanceLink;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopWfStepInstanceLinkPkBuilder{
    private Object[] values = new Object[3];

   
    public NopWfStepInstanceLinkPkBuilder setWfId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopWfStepInstanceLinkPkBuilder setStepId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   
    public NopWfStepInstanceLinkPkBuilder setPrevStepId(java.lang.String value){
        this.values[2] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopWfStepInstanceLink.PK_PROP_NAMES,values);
    }
}
