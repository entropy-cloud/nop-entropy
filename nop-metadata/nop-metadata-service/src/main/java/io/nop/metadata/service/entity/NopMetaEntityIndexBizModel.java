
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaEntityIndexBiz;
import io.nop.metadata.dao.entity.NopMetaEntityIndex;

@BizModel("NopMetaEntityIndex")
public class NopMetaEntityIndexBizModel extends CrudBizModel<NopMetaEntityIndex> implements INopMetaEntityIndexBiz{
    public NopMetaEntityIndexBizModel(){
        setEntityName(NopMetaEntityIndex.class.getName());
    }
}
