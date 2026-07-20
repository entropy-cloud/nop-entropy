
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaClassificationBiz;
import io.nop.metadata.dao.entity.NopMetaClassification;

@BizModel("NopMetaClassification")
public class NopMetaClassificationBizModel extends CrudBizModel<NopMetaClassification> implements INopMetaClassificationBiz{
    public NopMetaClassificationBizModel(){
        setEntityName(NopMetaClassification.class.getName());
    }
}
