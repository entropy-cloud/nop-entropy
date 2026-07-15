
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaTableDimensionBiz;
import io.nop.metadata.dao.entity.NopMetaTableDimension;

@BizModel("NopMetaTableDimension")
public class NopMetaTableDimensionBizModel extends CrudBizModel<NopMetaTableDimension> implements INopMetaTableDimensionBiz{
    public NopMetaTableDimensionBizModel(){
        setEntityName(NopMetaTableDimension.class.getName());
    }
}
