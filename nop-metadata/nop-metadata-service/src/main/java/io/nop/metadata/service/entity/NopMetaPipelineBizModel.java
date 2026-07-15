
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaPipelineBiz;
import io.nop.metadata.dao.entity.NopMetaPipeline;

@BizModel("NopMetaPipeline")
public class NopMetaPipelineBizModel extends CrudBizModel<NopMetaPipeline> implements INopMetaPipelineBiz{
    public NopMetaPipelineBizModel(){
        setEntityName(NopMetaPipeline.class.getName());
    }
}
