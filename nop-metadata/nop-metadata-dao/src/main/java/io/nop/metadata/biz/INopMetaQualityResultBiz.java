
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.orm.biz.ICrudBiz;


public interface INopMetaQualityResultBiz extends ICrudBiz<NopMetaQualityResult> {

    @BizMutation
    NopMetaQualityResult approve(@Name("id") String id, IServiceContext context);

    @BizMutation
    NopMetaQualityResult reject(@Name("id") String id, IServiceContext context);
}
