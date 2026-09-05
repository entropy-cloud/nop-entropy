/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.ITccRecordStore;
import io.nop.tcc.api.TccStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

import static io.nop.tcc.core.TccCoreErrors.ARG_TCC_STATUS;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_GROUP;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_ID;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_INVALID_CANCEL_BRANCH_STATUS;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS;

public class TccBranchTransaction implements ITccBranchTransaction {
    static final Logger LOG = LoggerFactory.getLogger(TccBranchTransaction.class);

    private final TccEngine tccEngine;
    private final ITccBranchRecord branchRecord;

    public TccBranchTransaction(ITccBranchRecord branchRecord, TccEngine tccEngine) {
        this.tccEngine = tccEngine;
        this.branchRecord = branchRecord;
    }

    public String toString() {
        return "TccBranchTransaction[branchId=" + getBranchId() + ",txnId=" + getTxnId()
                + ",txnGroup=" + getTxnGroup() + ",status=" + getBranchStatus() + "]";
    }

    protected ITccRecordStore getRepository() {
        return tccEngine.getTccRecordRepository();
    }

    @Override
    public CompletionStage<Void> beginTryAsync() {
        LOG.info("nop.tcc.branch-begin-try:{}", this);
        return getRepository().saveBranchRecordAsync(branchRecord, TccStatus.TRYING);
    }

    @Override
    public CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex) {
        LOG.info("nop.tcc.branch-finish-try:{}", this, ex);
        if (response != null)
            LOG.debug("nop.tcc.branch-finish-try-response:ok={},response={}", response.isOk(), JsonTool.stringify(response));

        if (ex != null) {
            if (tccEngine.isSafeFailException(ex)) {
                return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.TRY_FAILED, ex);
            } else {
                return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.TRY_UNKNOWN, ex);
            }
        }

        if (response == null || response.isBizSuccess()) {
            if (StringHelper.isEmpty(branchRecord.getConfirmMethod())) {
                return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_SUCCESS, null);
            } else {
                return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.TRY_SUCCESS, null);
            }
        }

        // 业务失败响应同样要记录失败原因，便于排查（此前error传null丢失诊断信息）
        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.TRY_FAILED,
                toResponseError(response));
    }

    private static Throwable toResponseError(ApiResponse<?> response) {
        if (response == null || StringHelper.isEmpty(response.getCode()))
            return null;
        // errorCode定位失败来源，description保留响应消息便于排查
        return new NopException(response.getCode(), null, false, false)
                .description(response.getMsg());
    }

    @Override
    public CompletionStage<Void> beginConfirmAsync() {
        LOG.info("nop.tcc.branch-begin-confirm:{}", this);
        TccStatus status = branchRecord.getBranchStatus();
        // CONFIRMING/CONFIRM_FAILED 允许重入：confirm阶段一旦开始就必须重试到最终成功，
        // 崩溃恢复时会从这两个状态续跑confirm
        if (status != TccStatus.TRY_SUCCESS && status != TccStatus.CONFIRMING
                && status != TccStatus.CONFIRM_FAILED && status != TccStatus.CONFIRM_SUCCESS)
            throw new NopException(ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS)
                    .param(ARG_TXN_GROUP, branchRecord.getTxnGroup())
                    .param(ARG_TXN_ID, branchRecord.getTxnId())
                    .param(ARG_TCC_STATUS, status);
        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRMING, null);
    }

    @Override
    public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
        LOG.info("nop.tcc.branch-finish-confirm:{}", this, ex);
        if (response != null)
            LOG.debug("nop.tcc.branch-finish-confirm-response:ok={},response={}", response.isOk(), JsonTool.stringify(response));

        if (ex != null) {
            // RPC以异常完成（网络故障、超时等）时结果未知，必须按失败记录以便恢复循环重试。
            // 此前ex被忽略且response==null被误判为成功，confirm异常被写入终态CONFIRM_SUCCESS
            return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_FAILED, ex);
        }

        if (response != null && response.isBizSuccess()) {
            return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_SUCCESS, null);
        }
        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_FAILED, null);
    }

    @Override
    public CompletionStage<Void> beginCancelAsync(boolean timeout) {
        LOG.info("nop.tcc.branch-begin-cancel:{}", this);

        // 可取消判定必须包含TRY_SUCCESS（业务失败回滚时成功try的分支是cancel的主要目标）。
        // isRollbackOnly()对TRY_SUCCESS返回false（allowConfirm状态），此前用它做守卫导致
        // 成功try的分支无法被cancel，补偿被跳过且全局误报CANCEL_SUCCESS
        if (!TccRunner.isBranchCancellable(branchRecord.getBranchStatus()))
            throw new NopException(ERR_TCC_INVALID_CANCEL_BRANCH_STATUS)
                    .param(ARG_TXN_GROUP, branchRecord.getTxnGroup())
                    .param(ARG_TXN_ID, branchRecord.getTxnId())
                    .param(ARG_TCC_STATUS, branchRecord.getBranchStatus());
        return getRepository().updateTccBranchStatusAsync(branchRecord,
                timeout ? TccStatus.BEFORE_TIMEOUT : TccStatus.CANCELLING, null);
    }

    @Override
    public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
        LOG.info("nop.tcc.branch-finish-cancel:{}", this, ex);
        if (response != null)
            LOG.debug("nop.tcc.branch-finish-cancel-response:ok={},response={}", response.isOk(), JsonTool.stringify(response));

        if (ex != null) {
            // RPC以异常完成时结果未知，按取消失败记录以便恢复循环重试。
            // 此前ex被忽略且response==null被误判为成功，cancel异常被写入终态CANCEL_SUCCESS
            return getRepository().updateTccBranchStatusAsync(branchRecord,
                    timeout ? TccStatus.TIMEOUT_FAILED : TccStatus.CANCEL_FAILED, ex);
        }

        if (response != null && response.isBizSuccess()) {
            return getRepository().updateTccBranchStatusAsync(branchRecord,
                    timeout ? TccStatus.TIMEOUT_SUCCESS : TccStatus.CANCEL_SUCCESS, ex);
        }

        if (Boolean.TRUE.equals(response.getBizFatal())) {
            return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.BIZ_CANCEL_FAILED, ex);
        }

        return getRepository().updateTccBranchStatusAsync(branchRecord,
                timeout ? TccStatus.TIMEOUT_FAILED : TccStatus.CANCEL_FAILED, ex);
    }

    public ITccBranchRecord getBranchRecord() {
        return branchRecord;
    }
}