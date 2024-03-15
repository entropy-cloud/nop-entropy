package demo.orm.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import demo.orm.entity.PreReq;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class PreReqPkBuilder{
    private Object[] values = new Object[2];

   
    public PreReqPkBuilder setCourseId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public PreReqPkBuilder setPreReqId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(PreReq.PK_PROP_NAMES,values);
    }
}
