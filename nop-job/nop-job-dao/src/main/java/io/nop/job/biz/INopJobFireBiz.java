
package io.nop.job.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.job.dao.entity.NopJobFire;

public interface INopJobFireBiz extends ICrudBiz<NopJobFire>{
    @BizMutation("cancelFire")
    void cancelFire(@Name("id") String id, IServiceContext context);

    @BizMutation("rerunFire")
    void rerunFire(@Name("id") String id, IServiceContext context);
}
