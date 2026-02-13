
package io.nop.retry.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.retry.biz.INopRetryRecordBiz;
import io.nop.retry.dao.entity.NopRetryRecord;

@BizModel("NopRetryRecord")
public class NopRetryRecordBizModel extends CrudBizModel<NopRetryRecord> implements INopRetryRecordBiz{
    public NopRetryRecordBizModel(){
        setEntityName(NopRetryRecord.class.getName());
    }
}
