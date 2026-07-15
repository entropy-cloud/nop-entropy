
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaTableMeasureBiz;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;

@BizModel("NopMetaTableMeasure")
public class NopMetaTableMeasureBizModel extends CrudBizModel<NopMetaTableMeasure> implements INopMetaTableMeasureBiz{
    public NopMetaTableMeasureBizModel(){
        setEntityName(NopMetaTableMeasure.class.getName());
    }
}
