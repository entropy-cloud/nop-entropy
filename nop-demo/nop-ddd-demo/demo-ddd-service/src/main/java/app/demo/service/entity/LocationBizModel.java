
package app.demo.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import app.demo.ddd.entity.Location;

@BizModel("Location")
public class LocationBizModel extends CrudBizModel<Location>{
    public LocationBizModel(){
        setEntityName(Location.class.getName());
    }
}
