
package io.nop.retry.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.retry.biz.INopRetryDeadLetterBiz;
import io.nop.retry.dao.entity.NopRetryDeadLetter;

@BizModel("NopRetryDeadLetter")
public class NopRetryDeadLetterBizModel extends CrudBizModel<NopRetryDeadLetter> implements INopRetryDeadLetterBiz{
    public NopRetryDeadLetterBizModel(){
        setEntityName(NopRetryDeadLetter.class.getName());
    }
}
