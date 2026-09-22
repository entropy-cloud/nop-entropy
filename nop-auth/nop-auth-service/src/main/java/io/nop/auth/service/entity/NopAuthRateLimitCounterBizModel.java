
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthRateLimitCounterBiz;
import io.nop.auth.dao.entity.NopAuthRateLimitCounter;

@BizModel("NopAuthRateLimitCounter")
public class NopAuthRateLimitCounterBizModel extends CrudBizModel<NopAuthRateLimitCounter> implements INopAuthRateLimitCounterBiz{
    public NopAuthRateLimitCounterBizModel(){
        setEntityName(NopAuthRateLimitCounter.class.getName());
    }
}
