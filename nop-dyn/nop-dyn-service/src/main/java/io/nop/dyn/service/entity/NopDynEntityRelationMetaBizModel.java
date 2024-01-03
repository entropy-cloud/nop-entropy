
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynEntityRelationMeta;

@BizModel("NopDynEntityRelationMeta")
public class NopDynEntityRelationMetaBizModel extends CrudBizModel<NopDynEntityRelationMeta>{
    public NopDynEntityRelationMetaBizModel(){
        setEntityName(NopDynEntityRelationMeta.class.getName());
    }
}
