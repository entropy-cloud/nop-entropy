
package app.demo.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import app.demo.ddd.entity.Voyage;

@BizModel("Voyage")
public class VoyageBizModel extends CrudBizModel<Voyage>{
    public VoyageBizModel(){
        setEntityName(Voyage.class.getName());
    }
}
