package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaDataContractBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.contract.MetaContractChecker;
import jakarta.inject.Inject;

import java.util.Map;

import static io.nop.metadata.service.NopMetadataErrors.ERR_CONTRACT_NOT_FOUND;

@BizModel("NopMetaDataContract")
public class NopMetaDataContractBizModel extends CrudBizModel<NopMetaDataContract> implements INopMetaDataContractBiz {

    @Inject
    protected MetaContractChecker contractChecker;

    public NopMetaDataContractBizModel() {
        setEntityName(NopMetaDataContract.class.getName());
    }

    // ============================================================
    // Approval action overrides (取代 approval-support.xbiz 版本，
    // 追加业务状态转换 DRAFT→ACTIVE / ACTIVE→DEPRECATED / DEPRECATED→RETIRED)
    // ============================================================

    @BizMutation
    public NopMetaDataContract approve(@Name("id") String id, IServiceContext context) {
        NopMetaDataContract entity = dao().getEntityById(id);
        if (entity == null)
            throw new NopException(ERR_CONTRACT_NOT_FOUND).param("contractId", id);
        String approveStatus = entity.getApproveStatus();
        if (!"SUBMITTED".equals(approveStatus)) {
            throw new NopException(NopMetadataErrors.ERR_CONTRACT_INVALID_TRANSITION)
                    .param("contractId", id)
                    .param("currentStatus", approveStatus)
                    .param("expectedStatus", "SUBMITTED");
        }
        entity.setApproveStatus("APPROVED");
        entity.setApprovedBy(context.getUserId());
        entity.setApprovedAt(new java.sql.Timestamp(CoreMetrics.currentTimeMillis()));

        String status = entity.getStatus();
        if (_NopMetadataCoreConstants.CONTRACT_STATUS_DRAFT.equals(status)) {
            entity.setStatus(_NopMetadataCoreConstants.CONTRACT_STATUS_ACTIVE);
        } else if (_NopMetadataCoreConstants.CONTRACT_STATUS_ACTIVE.equals(status)) {
            entity.setStatus(_NopMetadataCoreConstants.CONTRACT_STATUS_DEPRECATED);
        } else if (_NopMetadataCoreConstants.CONTRACT_STATUS_DEPRECATED.equals(status)) {
            entity.setStatus(_NopMetadataCoreConstants.CONTRACT_STATUS_RETIRED);
        }

        dao().updateEntity(entity);
        return entity;
    }

    @BizMutation
    public NopMetaDataContract reject(@Name("id") String id, IServiceContext context) {
        NopMetaDataContract entity = dao().getEntityById(id);
        if (entity == null)
            throw new NopException(ERR_CONTRACT_NOT_FOUND).param("contractId", id);
        String approveStatus = entity.getApproveStatus();
        if (!"SUBMITTED".equals(approveStatus)) {
            throw new NopException(NopMetadataErrors.ERR_CONTRACT_INVALID_TRANSITION)
                    .param("contractId", id)
                    .param("currentStatus", approveStatus)
                    .param("expectedStatus", "SUBMITTED");
        }
        entity.setApproveStatus("REJECTED");
        entity.setApprovedBy(context.getUserId());
        entity.setApprovedAt(new java.sql.Timestamp(CoreMetrics.currentTimeMillis()));
        entity.setStatus(_NopMetadataCoreConstants.CONTRACT_STATUS_DRAFT);
        entity.setRemark("Rejected: " + (entity.getRemark() != null ? entity.getRemark() : "No reason provided"));

        dao().updateEntity(entity);
        return entity;
    }

    // ============================================================
    // 已废弃的状态生命周期方法 — 委托给 submitForApproval
    // ============================================================

    @Deprecated
    @BizMutation
    public NopMetaDataContract activateContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopException(ERR_CONTRACT_NOT_FOUND).param("contractId", contractId);
        checkDataAuth(io.nop.biz.BizConstants.METHOD_UPDATE, contract, context);
        return submitForApproval(contractId, context);
    }

    @Deprecated
    @BizMutation
    public NopMetaDataContract deprecateContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopException(ERR_CONTRACT_NOT_FOUND).param("contractId", contractId);
        checkDataAuth(io.nop.biz.BizConstants.METHOD_UPDATE, contract, context);
        return submitForApproval(contractId, context);
    }

    @Deprecated
    @BizMutation
    public NopMetaDataContract retireContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopException(ERR_CONTRACT_NOT_FOUND).param("contractId", contractId);
        checkDataAuth(io.nop.biz.BizConstants.METHOD_UPDATE, contract, context);
        return submitForApproval(contractId, context);
    }

    // ============================================================
    // 契约检查（D2 钉死语义）
    // ============================================================

    @BizMutation
    public Map<String, Object> checkContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null) {
            throw new NopException(ERR_CONTRACT_NOT_FOUND).param("contractId", contractId);
        }

        Map<String, Object> result = contractChecker.check(
                contractId,
                contract.getEntityTableId(),
                contract.getQualityExpectations(),
                contract.getSla());

        contract.setLatestResult(JsonTool.stringify(result));
        dao().updateEntity(contract);
        return result;
    }

    // ============================================================
    // 将审批委托给 submitForApproval（由 approval-support.xbiz 提供，
    // 经 x:extends 合并到 dispatch 层）
    // ============================================================

    private NopMetaDataContract submitForApproval(String contractId, IServiceContext context) {
        return (NopMetaDataContract) bizObjectManager().getBizObject("NopMetaDataContract")
                .invoke("submitForApproval", Map.of("id", contractId), null, context);
    }
}
