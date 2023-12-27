
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynPage;

@BizModel("NopDynPage")
public class NopDynPageBizModel extends CrudBizModel<NopDynPage>{
    public NopDynPageBizModel(){
        setEntityName(NopDynPage.class.getName());
    }
}
