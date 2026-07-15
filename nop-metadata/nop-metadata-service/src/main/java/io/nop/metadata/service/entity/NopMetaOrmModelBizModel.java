
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaOrmModelBiz;
import io.nop.metadata.dao.entity.NopMetaOrmModel;

@BizModel("NopMetaOrmModel")
public class NopMetaOrmModelBizModel extends CrudBizModel<NopMetaOrmModel> implements INopMetaOrmModelBiz{
    public NopMetaOrmModelBizModel(){
        setEntityName(NopMetaOrmModel.class.getName());
    }
}
