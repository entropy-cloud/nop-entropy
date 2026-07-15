
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaSemanticTypeBiz;
import io.nop.metadata.dao.entity.NopMetaSemanticType;

@BizModel("NopMetaSemanticType")
public class NopMetaSemanticTypeBizModel extends CrudBizModel<NopMetaSemanticType> implements INopMetaSemanticTypeBiz{
    public NopMetaSemanticTypeBizModel(){
        setEntityName(NopMetaSemanticType.class.getName());
    }
}
