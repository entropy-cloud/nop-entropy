package demo.orm.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import demo.orm.entity.Taking;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class TakingPkBuilder{
    private Object[] values = new Object[5];

   
    public TakingPkBuilder setStudentId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public TakingPkBuilder setCourseId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   
    public TakingPkBuilder setSecId(java.lang.String value){
        this.values[2] = value;
        return this;
    }
   
    public TakingPkBuilder setSemester(java.lang.String value){
        this.values[3] = value;
        return this;
    }
   
    public TakingPkBuilder setYear(java.math.BigDecimal value){
        this.values[4] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(Taking.PK_PROP_NAMES,values);
    }
}
