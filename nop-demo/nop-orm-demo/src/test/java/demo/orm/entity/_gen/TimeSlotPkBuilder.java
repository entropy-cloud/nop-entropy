package demo.orm.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import demo.orm.entity.TimeSlot;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class TimeSlotPkBuilder{
    private Object[] values = new Object[4];

   
    public TimeSlotPkBuilder setTimeSlotId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public TimeSlotPkBuilder setDay(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   
    public TimeSlotPkBuilder setStartHr(java.math.BigDecimal value){
        this.values[2] = value;
        return this;
    }
   
    public TimeSlotPkBuilder setStartMin(java.math.BigDecimal value){
        this.values[3] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(TimeSlot.PK_PROP_NAMES,values);
    }
}
