package demo.orm.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import demo.orm.entity._gen._Teaching;

import demo.orm.entity._gen.TeachingPkBuilder;


@BizObjName("Teaching")
public class Teaching extends _Teaching{


    public static TeachingPkBuilder newPk(){
        return new TeachingPkBuilder();
    }

}
