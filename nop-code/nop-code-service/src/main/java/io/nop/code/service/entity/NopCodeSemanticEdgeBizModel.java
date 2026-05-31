package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeSemanticEdgeBiz;
import io.nop.code.dao.entity.NopCodeSemanticEdge;
@BizModel("NopCodeSemanticEdge")
public class NopCodeSemanticEdgeBizModel extends CrudBizModel<NopCodeSemanticEdge> implements INopCodeSemanticEdgeBiz{
    public NopCodeSemanticEdgeBizModel(){
        setEntityName(NopCodeSemanticEdge.class.getName());
    }
}
