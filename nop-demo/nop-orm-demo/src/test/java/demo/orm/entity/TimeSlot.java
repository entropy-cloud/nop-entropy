package demo.orm.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import demo.orm.entity._gen._TimeSlot;

import demo.orm.entity._gen.TimeSlotPkBuilder;


@BizObjName("TimeSlot")
public class TimeSlot extends _TimeSlot{


    public static TimeSlotPkBuilder newPk(){
        return new TimeSlotPkBuilder();
    }

}
