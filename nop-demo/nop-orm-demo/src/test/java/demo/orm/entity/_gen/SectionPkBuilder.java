package demo.orm.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import demo.orm.entity.Section;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class SectionPkBuilder{
    private Object[] values = new Object[4];

   
    public SectionPkBuilder setCourseId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public SectionPkBuilder setSecId(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   
    public SectionPkBuilder setSemester(java.lang.String value){
        this.values[2] = value;
        return this;
    }
   
    public SectionPkBuilder setYear(java.math.BigDecimal value){
        this.values[3] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(Section.PK_PROP_NAMES,values);
    }
}
