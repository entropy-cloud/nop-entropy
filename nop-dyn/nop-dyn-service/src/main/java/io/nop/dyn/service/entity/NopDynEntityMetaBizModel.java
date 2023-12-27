
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynEntityMeta;

@BizModel("NopDynEntityMeta")
public class NopDynEntityMetaBizModel extends CrudBizModel<NopDynEntityMeta>{
    public NopDynEntityMetaBizModel(){
        setEntityName(NopDynEntityMeta.class.getName());
    }
}
