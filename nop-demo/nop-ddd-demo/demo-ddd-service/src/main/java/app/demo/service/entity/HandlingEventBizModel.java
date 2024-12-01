
package app.demo.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import app.demo.ddd.entity.HandlingEvent;

@BizModel("HandlingEvent")
public class HandlingEventBizModel extends CrudBizModel<HandlingEvent>{
    public HandlingEventBizModel(){
        setEntityName(HandlingEvent.class.getName());
    }
}
