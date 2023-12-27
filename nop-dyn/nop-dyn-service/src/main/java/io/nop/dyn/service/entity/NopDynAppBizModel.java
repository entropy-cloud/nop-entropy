
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynApp;

@BizModel("NopDynApp")
public class NopDynAppBizModel extends CrudBizModel<NopDynApp>{
    public NopDynAppBizModel(){
        setEntityName(NopDynApp.class.getName());
    }
}
