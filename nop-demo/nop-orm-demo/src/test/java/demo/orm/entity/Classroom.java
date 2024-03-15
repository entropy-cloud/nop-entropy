package demo.orm.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import demo.orm.entity._gen._Classroom;

import demo.orm.entity._gen.ClassroomPkBuilder;


@BizObjName("Classroom")
public class Classroom extends _Classroom{


    public static ClassroomPkBuilder newPk(){
        return new ClassroomPkBuilder();
    }

}
