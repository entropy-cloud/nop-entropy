package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaDataContractBiz;
import io.nop.metadata.api.dto.ContractCheckResultDTO;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.service.contract.MetaContractChecker;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Data Contract BizModel.
 *
 * <p>approve/reject 的单一事实源已裁定为保留层 XPL（NopMetaDataContract.xbiz 的 approve/reject
 * mutation，含 DRAFT→ACTIVE→DEPRECATED→RETIRED 状态生命周期）；Java 实现已删除，经 GraphQL/RPC
 * 调用的是 XPL 版本（plan 2026-08-04-1543-3 R2.2）。
 */
@BizModel("NopMetaDataContract")
public class NopMetaDataContractBizModel extends CrudBizModel<NopMetaDataContract> implements INopMetaDataContractBiz {

    @Inject
    protected MetaContractChecker contractChecker;

    public NopMetaDataContractBizModel() {
        setEntityName(NopMetaDataContract.class.getName());
    }

    @SuppressWarnings("unchecked")
    @BizMutation
    public ContractCheckResultDTO checkContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = requireEntity(contractId, "check", context);

        Map<String, Object> result = contractChecker.check(
                contractId,
                contract.getMetaTableId(),
                contract.getQualityExpectations(),
                contract.getSla());

        contract.setLatestResult(JsonTool.stringify(result));
        dao().updateEntity(contract);

        ContractCheckResultDTO dto = new ContractCheckResultDTO();
        if (result.get("timestamp") instanceof java.sql.Timestamp) {
            dto.setTimestamp((java.sql.Timestamp) result.get("timestamp"));
        }
        dto.setStatus((String) result.get("status"));
        dto.setMessage((String) result.get("message"));
        if (result.get("qualitySummary") instanceof Map) {
            dto.setQualitySummary((Map<String, Object>) result.get("qualitySummary"));
        }
        if (result.get("slaSummary") instanceof Map) {
            dto.setSlaSummary((Map<String, Object>) result.get("slaSummary"));
        }
        return dto;
    }

    @SuppressWarnings("unchecked")
    @BizQuery
    public ContractCheckResultDTO checkContractReadOnly(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = requireEntity(contractId, "check", context);

        Map<String, Object> result = contractChecker.check(
                contractId,
                contract.getMetaTableId(),
                contract.getQualityExpectations(),
                contract.getSla());

        ContractCheckResultDTO dto = new ContractCheckResultDTO();
        if (result.get("timestamp") instanceof java.sql.Timestamp) {
            dto.setTimestamp((java.sql.Timestamp) result.get("timestamp"));
        }
        dto.setStatus((String) result.get("status"));
        dto.setMessage((String) result.get("message"));
        if (result.get("qualitySummary") instanceof Map) {
            dto.setQualitySummary((Map<String, Object>) result.get("qualitySummary"));
        }
        if (result.get("slaSummary") instanceof Map) {
            dto.setSlaSummary((Map<String, Object>) result.get("slaSummary"));
        }
        return dto;
    }

}
