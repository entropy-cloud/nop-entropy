package demo.orm.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import demo.orm.entity.Classroom;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class ClassroomPkBuilder{
    private Object[] values = new Object[2];

   
    public ClassroomPkBuilder setBuilding(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public ClassroomPkBuilder setRoomNumber(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(Classroom.PK_PROP_NAMES,values);
    }
}
