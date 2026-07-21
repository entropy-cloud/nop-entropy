
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.orm.biz.ICrudBiz;

import java.util.Map;


/**
 * NopMetaDataContract BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>跨模块 {@code @Inject INopMetaDataContractBiz} 调用入口：
 * activateContract / deprecateContract / retireContract / checkContract。
 */
public interface INopMetaDataContractBiz extends ICrudBiz<NopMetaDataContract> {

    @Deprecated
    @BizMutation
    NopMetaDataContract activateContract(@Name("contractId") String contractId, IServiceContext context);

    @Deprecated
    @BizMutation
    NopMetaDataContract deprecateContract(@Name("contractId") String contractId, IServiceContext context);

    @Deprecated
    @BizMutation
    NopMetaDataContract retireContract(@Name("contractId") String contractId, IServiceContext context);

    @BizMutation
    Map<String, Object> checkContract(@Name("contractId") String contractId, IServiceContext context);

    @BizMutation
    NopMetaDataContract approve(@Name("id") String id, IServiceContext context);

    @BizMutation
    NopMetaDataContract reject(@Name("id") String id, IServiceContext context);
}
