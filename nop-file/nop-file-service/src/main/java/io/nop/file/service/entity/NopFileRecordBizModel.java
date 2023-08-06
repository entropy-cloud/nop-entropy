
package io.nop.file.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.file.dao.entity.NopFileRecord;

@BizModel("NopFileRecord")
public class NopFileRecordBizModel extends CrudBizModel<NopFileRecord>{
    public NopFileRecordBizModel(){
        setEntityName(NopFileRecord.class.getName());
    }
}
