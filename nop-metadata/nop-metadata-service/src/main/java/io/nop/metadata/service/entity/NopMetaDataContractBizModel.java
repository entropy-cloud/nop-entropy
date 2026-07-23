package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.time.CoreMetrics;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaDataContractBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.api.dto.ContractCheckResultDTO;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.contract.MetaContractChecker;
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;

import java.util.Map;

import static io.nop.metadata.service.NopMetadataErrors.ARG_CONTRACT_ID;
import static io.nop.metadata.service.NopMetadataErrors.ERR_CONTRACT_NOT_FOUND;

/**
 * Data Contract BizModel.
 * <p>The {@code submitForApproval} method uses reflective
 * {@code bizObjectManager().getBizObject(...).invoke("submitForApproval", ...)} which requires the
 * {@code approval-support.xbiz} delta to be deployed. Without that delta, the method will fail at runtime.</p>
 */
@BizModel("NopMetaDataContract")
public class NopMetaDataContractBizModel extends CrudBizModel<NopMetaDataContract> implements INopMetaDataContractBiz {

    @Inject
    protected MetaContractChecker contractChecker;

    public NopMetaDataContractBizModel() {
        setEntityName(NopMetaDataContract.class.getName());
    }

    @BizMutation
    public NopMetaDataContract approve(@Name("id") String id, IServiceContext context) {
        return txn().runInTransaction(txn -> {
            NopMetaDataContract entity = dao().getEntityById(id);
            if (entity == null)
                throw new NopMetadataException(ERR_CONTRACT_NOT_FOUND).param(ARG_CONTRACT_ID, id);
            checkDataAuth("approve", entity, context);

            String approveStatus = entity.getApproveStatus();
            if (!"SUBMITTED".equals(approveStatus)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_CONTRACT_INVALID_TRANSITION)
                        .param(ARG_CONTRACT_ID, id)
                        .param(NopMetadataErrors.ARG_CURRENT_STATUS, approveStatus)
                        .param(NopMetadataErrors.ARG_EXPECTED_STATUS, "SUBMITTED");
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
        });
    }

    @BizMutation
    public NopMetaDataContract reject(@Name("id") String id, IServiceContext context) {
        return txn().runInTransaction(txn -> {
            NopMetaDataContract entity = dao().getEntityById(id);
            if (entity == null)
                throw new NopMetadataException(ERR_CONTRACT_NOT_FOUND).param(ARG_CONTRACT_ID, id);
            checkDataAuth("reject", entity, context);

            String approveStatus = entity.getApproveStatus();
            if (!"SUBMITTED".equals(approveStatus)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_CONTRACT_INVALID_TRANSITION)
                        .param(ARG_CONTRACT_ID, id)
                        .param(NopMetadataErrors.ARG_CURRENT_STATUS, approveStatus)
                        .param(NopMetadataErrors.ARG_EXPECTED_STATUS, "SUBMITTED");
            }
            entity.setApproveStatus("REJECTED");
            entity.setApprovedBy(context.getUserId());
            entity.setApprovedAt(new java.sql.Timestamp(CoreMetrics.currentTimeMillis()));
            entity.setStatus(_NopMetadataCoreConstants.CONTRACT_STATUS_DRAFT);
            entity.setRemark("Rejected: " + (entity.getRemark() != null ? entity.getRemark() : "No reason provided"));

            dao().updateEntity(entity);
            return entity;
        });
    }

    @Deprecated
    @BizMutation
    public NopMetaDataContract activateContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopMetadataException(ERR_CONTRACT_NOT_FOUND).param(ARG_CONTRACT_ID, contractId);
        checkDataAuth(io.nop.biz.BizConstants.METHOD_UPDATE, contract, context);
        return submitForApproval(contractId, context);
    }

    @Deprecated
    @BizMutation
    public NopMetaDataContract deprecateContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopMetadataException(ERR_CONTRACT_NOT_FOUND).param(ARG_CONTRACT_ID, contractId);
        checkDataAuth(io.nop.biz.BizConstants.METHOD_UPDATE, contract, context);
        return submitForApproval(contractId, context);
    }

    @Deprecated
    @BizMutation
    public NopMetaDataContract retireContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopMetadataException(ERR_CONTRACT_NOT_FOUND).param(ARG_CONTRACT_ID, contractId);
        checkDataAuth(io.nop.biz.BizConstants.METHOD_UPDATE, contract, context);
        return submitForApproval(contractId, context);
    }

    @SuppressWarnings("unchecked")
    @BizMutation
    public ContractCheckResultDTO checkContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopMetadataException(ERR_CONTRACT_NOT_FOUND).param(ARG_CONTRACT_ID, contractId);
        checkDataAuth("check", contract, context);

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
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null)
            throw new NopMetadataException(ERR_CONTRACT_NOT_FOUND).param(ARG_CONTRACT_ID, contractId);
        checkDataAuth("check", contract, context);

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

    private NopMetaDataContract submitForApproval(String contractId, IServiceContext context) {
        return (NopMetaDataContract) bizObjectManager().getBizObject("NopMetaDataContract")
                .invoke("submitForApproval", Map.of("id", contractId), null, context);
    }
}
