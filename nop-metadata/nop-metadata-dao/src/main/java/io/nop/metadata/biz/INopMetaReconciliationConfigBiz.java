
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;


public interface INopMetaReconciliationConfigBiz extends ICrudBiz<NopMetaReconciliationConfig>{

    @BizMutation
    NopMetaReconciliationResult executeReconciliation(@Name("configId") String configId, IServiceContext context);
}
