
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysCheckerRecord;

@BizModel("NopSysCheckerRecord")
public class NopSysCheckerRecordBizModel extends CrudBizModel<NopSysCheckerRecord>{
    public NopSysCheckerRecordBizModel(){
        setEntityName(NopSysCheckerRecord.class.getName());
    }
}
