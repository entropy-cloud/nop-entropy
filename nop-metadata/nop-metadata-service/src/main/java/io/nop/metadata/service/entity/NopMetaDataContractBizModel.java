/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaDataContractBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.service.contract.MetaContractChecker;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * 数据契约 BizModel：基线 CRUD（{@link CrudBizModel}）+ 契约状态生命周期 + 契约检查（设计 04 §2.3/§5.2，plan 0900-1）。
 *
 * <p>状态生命周期（D1 钉死 4 值流转）：
 * <ul>
 *   <li>{@code activateContract}：DRAFT→ACTIVE。</li>
 *   <li>{@code deprecateContract}：ACTIVE→DEPRECATED。</li>
 *   <li>{@code retireContract}：DEPRECATED→RETIRED。</li>
 *   <li>非法前置状态（如 DRAFT→RETIRED、RETIRED→*、已 RETIRED 再流转）→ 显式抛 {@link #NopMetadataErrors.ERR_CONTRACT_INVALID_TRANSITION}
 *       （不静默跳过、不静默改状态）。</li>
 * </ul>
 *
 * <p>契约检查（D2 钉死语义）：{@code checkContract(contractId)} 不受 status 阻断（DRAFT 可预检），
 * 调用 {@link MetaContractChecker} 聚合质量结果 + SLA 新鲜度，写回实体 {@code latestResult}，返回结构化结果。
 *
 * <p>ErrorCode 按模块惯例内联于本类顶部（不写入空 interface {@code NopMetadataErrors}）。
 * 平台 IoC：{@code @Inject} 使用 {@code protected} 字段（AGENTS.md）。
 */
@BizModel("NopMetaDataContract")
public class NopMetaDataContractBizModel extends CrudBizModel<NopMetaDataContract> implements INopMetaDataContractBiz {


    @Inject
    protected MetaContractChecker contractChecker;

    public NopMetaDataContractBizModel() {
        setEntityName(NopMetaDataContract.class.getName());
    }

    // ============================================================
    // 状态生命周期（D1 钉死流转：DRAFT→ACTIVE→DEPRECATED→RETIRED）
    // ============================================================

    /** 激活契约：DRAFT → ACTIVE。非法前置状态显式失败。 */
    @BizMutation
    public NopMetaDataContract activateContract(@Name("contractId") String contractId, IServiceContext context) {
        return transition(contractId, _NopMetadataCoreConstants.CONTRACT_STATUS_ACTIVE,
                _NopMetadataCoreConstants.CONTRACT_STATUS_DRAFT, context);
    }

    /** 废弃契约：ACTIVE → DEPRECATED。非法前置状态显式失败。 */
    @BizMutation
    public NopMetaDataContract deprecateContract(@Name("contractId") String contractId, IServiceContext context) {
        return transition(contractId, _NopMetadataCoreConstants.CONTRACT_STATUS_DEPRECATED,
                _NopMetadataCoreConstants.CONTRACT_STATUS_ACTIVE, context);
    }

    /** 退役契约：DEPRECATED → RETIRED（终态）。非法前置状态显式失败。 */
    @BizMutation
    public NopMetaDataContract retireContract(@Name("contractId") String contractId, IServiceContext context) {
        return transition(contractId, _NopMetadataCoreConstants.CONTRACT_STATUS_RETIRED,
                _NopMetadataCoreConstants.CONTRACT_STATUS_DEPRECATED, context);
    }

    /**
     * 通用状态流转：校验前置状态合法后更新。
     *
     * @param contractId    契约 ID
     * @param targetStatus  目标状态
     * @param expectedPrior 合法前置状态（唯一）
     */
    private NopMetaDataContract transition(String contractId, String targetStatus, String expectedPrior,
                                           IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null) {
            throw new NopException(NopMetadataErrors.ERR_CONTRACT_NOT_FOUND).param("contractId", contractId);
        }
        String currentStatus = contract.getStatus();
        if (!expectedPrior.equals(currentStatus)) {
            throw new NopException(NopMetadataErrors.ERR_CONTRACT_INVALID_TRANSITION)
                    .param("contractId", contractId)
                    .param("currentStatus", currentStatus)
                    .param("expectedStatus", expectedPrior);
        }
        checkDataAuth(io.nop.biz.BizConstants.METHOD_UPDATE, contract, context);
        contract.setStatus(targetStatus);
        dao().updateEntity(contract);
        return contract;
    }

    // ============================================================
    // 契约检查（D2 钉死语义）
    // ============================================================

    /**
     * 执行契约检查（设计 04 §5.2 D2）。不受 status 阻断（DRAFT 可预检）。
     *
     * <p>行为：调 {@link MetaContractChecker} 聚合 qualityExpectations 引用的质量规则最新结果 +
     * 基于 Catalog 最新快照的 SLA 新鲜度判断 → 写回实体 {@code latestResult} → 返回结构化结果。
     *
     * @param contractId 契约 ID
     * @return {@code {timestamp, status, message, qualitySummary, slaSummary}}
     */
    @BizMutation
    public Map<String, Object> checkContract(@Name("contractId") String contractId, IServiceContext context) {
        NopMetaDataContract contract = dao().getEntityById(contractId);
        if (contract == null) {
            throw new NopException(NopMetadataErrors.ERR_CONTRACT_NOT_FOUND).param("contractId", contractId);
        }

        Map<String, Object> result = contractChecker.check(
                contractId,
                contract.getEntityTableId(),
                contract.getQualityExpectations(),
                contract.getSla());

        // 写回 latestResult（mediumtext + stdDomain json）
        contract.setLatestResult(JsonTool.stringify(result));
        dao().updateEntity(contract);
        return result;
    }
}
