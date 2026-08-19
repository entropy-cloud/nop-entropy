
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.biz.INopJobTaskLogBiz;
import io.nop.job.dao.entity.NopJobTaskLog;

@BizModel("NopJobTaskLog")
public class NopJobTaskLogBizModel extends CrudBizModel<NopJobTaskLog> implements INopJobTaskLogBiz{
    public NopJobTaskLogBizModel(){
        setEntityName(NopJobTaskLog.class.getName());
    }
}
