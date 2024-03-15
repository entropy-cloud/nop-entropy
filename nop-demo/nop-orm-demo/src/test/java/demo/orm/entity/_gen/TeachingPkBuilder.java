package demo.orm.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import demo.orm.entity.Teaching;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class TeachingPkBuilder{
    private Object[] values = new Object[5];

   
    public TeachingPkBuilder setInstructorId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public TeachingPkBuilder setCourseId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   
    public TeachingPkBuilder setSecId(java.lang.String value){
        this.values[2] = value;
        return this;
    }
   
    public TeachingPkBuilder setSemester(java.lang.String value){
        this.values[3] = value;
        return this;
    }
   
    public TeachingPkBuilder setYear(java.math.BigDecimal value){
        this.values[4] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(Teaching.PK_PROP_NAMES,values);
    }
}
