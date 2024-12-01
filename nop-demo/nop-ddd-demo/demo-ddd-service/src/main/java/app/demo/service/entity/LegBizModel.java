
package app.demo.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import app.demo.ddd.entity.Leg;

@BizModel("Leg")
public class LegBizModel extends CrudBizModel<Leg>{
    public LegBizModel(){
        setEntityName(Leg.class.getName());
    }
}
