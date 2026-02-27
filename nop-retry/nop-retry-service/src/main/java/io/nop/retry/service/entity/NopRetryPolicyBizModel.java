
package io.nop.retry.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.retry.biz.INopRetryPolicyBiz;
import io.nop.retry.dao.entity.NopRetryPolicy;

@BizModel("NopRetryPolicy")
public class NopRetryPolicyBizModel extends CrudBizModel<NopRetryPolicy> implements INopRetryPolicyBiz {
    public NopRetryPolicyBizModel() {
        setEntityName(NopRetryPolicy.class.getName());
    }
}
