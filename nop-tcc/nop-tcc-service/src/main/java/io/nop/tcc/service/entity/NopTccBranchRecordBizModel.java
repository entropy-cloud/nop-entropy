
package io.nop.tcc.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.tcc.dao.entity.NopTccBranchRecord;

@BizModel("NopTccBranchRecord")
public class NopTccBranchRecordBizModel extends CrudBizModel<NopTccBranchRecord>{
    public NopTccBranchRecordBizModel(){
        setEntityName(NopTccBranchRecord.class.getName());
    }
}
