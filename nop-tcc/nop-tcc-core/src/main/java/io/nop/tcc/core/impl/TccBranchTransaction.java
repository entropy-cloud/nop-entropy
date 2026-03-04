/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiResponse;
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

        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.TRY_FAILED, null);
    }

    @Override
    public CompletionStage<Void> beginConfirmAsync() {
        LOG.info("nop.tcc.branch-begin-confirm:{}", this);
        Guard.checkArgument(branchRecord.getBranchStatus().isAllowConfirm());
        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRMING, null);
    }

    @Override
    public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
        LOG.info("nop.tcc.branch-finish-confirm:{}", this, ex);
        if (response != null)
            LOG.debug("nop.tcc.branch-finish-confirm-response:ok={},response={}", response.isOk(), JsonTool.stringify(response));

        if (response == null || response.isBizSuccess()) {
            return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_SUCCESS, null);
        }
        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_FAILED, null);
    }

    @Override
    public CompletionStage<Void> beginCancelAsync(boolean timeout) {
        LOG.info("nop.tcc.branch-begin-cancel:{}", this);

        Guard.checkArgument(branchRecord.getBranchStatus().isRollbackOnly());
        return getRepository().updateTccBranchStatusAsync(branchRecord,
                timeout ? TccStatus.BEFORE_TIMEOUT : TccStatus.CANCELLING, null);
    }

    @Override
    public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
        LOG.info("nop.tcc.branch-finish-cancel:{}", this, ex);
        if (response != null)
            LOG.debug("nop.tcc.branch-finish-cancel-response:ok={},response={}", response.isOk(), JsonTool.stringify(response));

        if (response == null || response.isBizSuccess()) {
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