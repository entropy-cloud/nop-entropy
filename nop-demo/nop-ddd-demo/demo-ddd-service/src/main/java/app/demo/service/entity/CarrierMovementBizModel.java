
package app.demo.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import app.demo.ddd.entity.CarrierMovement;

@BizModel("CarrierMovement")
public class CarrierMovementBizModel extends CrudBizModel<CarrierMovement>{
    public CarrierMovementBizModel(){
        setEntityName(CarrierMovement.class.getName());
    }
}
