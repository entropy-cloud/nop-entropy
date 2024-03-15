package demo.orm.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import demo.orm.entity._gen._PreReq;

import demo.orm.entity._gen.PreReqPkBuilder;


@BizObjName("PreReq")
public class PreReq extends _PreReq{


    public static PreReqPkBuilder newPk(){
        return new PreReqPkBuilder();
    }

}
