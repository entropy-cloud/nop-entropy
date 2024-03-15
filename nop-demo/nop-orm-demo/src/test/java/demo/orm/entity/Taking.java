package demo.orm.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import demo.orm.entity._gen._Taking;

import demo.orm.entity._gen.TakingPkBuilder;


@BizObjName("Taking")
public class Taking extends _Taking{


    public static TakingPkBuilder newPk(){
        return new TakingPkBuilder();
    }

}
