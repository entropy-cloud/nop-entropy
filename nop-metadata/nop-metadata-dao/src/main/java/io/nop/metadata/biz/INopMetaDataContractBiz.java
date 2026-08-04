package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.api.dto.ContractCheckResultDTO;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.orm.biz.ICrudBiz;

/**
 * NopMetaDataContract BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>跨模块 {@code @Inject INopMetaDataContractBiz} 调用入口：
 * checkContract / checkContractReadOnly。
 *
 * <p>approve/reject 已从接口删除（plan 2026-08-04-1543-3 R2.2 单一事实源裁定 = 保留层
 * NopMetaDataContract.xbiz XPL），经 GraphQL/RPC 调用 XPL 版本。
 */
public interface INopMetaDataContractBiz extends ICrudBiz<NopMetaDataContract> {

    @BizMutation
    ContractCheckResultDTO checkContract(@Name("contractId") String contractId, IServiceContext context);

    @BizQuery
    ContractCheckResultDTO checkContractReadOnly(@Name("contractId") String contractId, IServiceContext context);
}
