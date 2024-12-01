
package app.demo.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import app.demo.ddd.entity.Cargo;

@BizModel("Cargo")
public class CargoBizModel extends CrudBizModel<Cargo>{
    public CargoBizModel(){
        setEntityName(Cargo.class.getName());
    }
}
