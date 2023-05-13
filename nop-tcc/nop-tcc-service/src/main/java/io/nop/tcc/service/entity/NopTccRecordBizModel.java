
package io.nop.tcc.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.tcc.dao.entity.NopTccRecord;

@BizModel("NopTccRecord")
public class NopTccRecordBizModel extends CrudBizModel<NopTccRecord>{
    public NopTccRecordBizModel(){
        setEntityName(NopTccRecord.class.getName());
    }
}
