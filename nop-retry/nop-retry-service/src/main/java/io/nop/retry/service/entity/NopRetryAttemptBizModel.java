
package io.nop.retry.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.retry.biz.INopRetryAttemptBiz;
import io.nop.retry.dao.entity.NopRetryAttempt;

@BizModel("NopRetryAttempt")
public class NopRetryAttemptBizModel extends CrudBizModel<NopRetryAttempt> implements INopRetryAttemptBiz{
    public NopRetryAttemptBizModel(){
        setEntityName(NopRetryAttempt.class.getName());
    }
}
