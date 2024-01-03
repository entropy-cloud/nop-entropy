
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynEntityRelation;

@BizModel("NopDynEntityRelation")
public class NopDynEntityRelationBizModel extends CrudBizModel<NopDynEntityRelation>{
    public NopDynEntityRelationBizModel(){
        setEntityName(NopDynEntityRelation.class.getName());
    }
}
