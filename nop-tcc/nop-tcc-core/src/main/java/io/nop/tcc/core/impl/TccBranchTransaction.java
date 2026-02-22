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
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.ITccRecordStore;
import io.nop.tcc.api.TccStatus;

import java.util.concurrent.CompletionStage;

public class TccBranchTransaction implements ITccBranchTransaction {
    private final TccEngine tccEngine;
    private final ITccBranchRecord branchRecord;

    public TccBranchTransaction(ITccBranchRecord branchRecord, TccEngine tccEngine) {
        this.tccEngine = tccEngine;
        this.branchRecord = branchRecord;
    }

    protected ITccRecordStore getRepository() {
        return tccEngine.getTccRecordRepository();
    }

    @Override
    public CompletionStage<Void> beginTryAsync() {
        return getRepository().saveBranchRecordAsync(branchRecord, TccStatus.TRYING);
    }

    @Override
    public CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex) {
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
        Guard.checkArgument(branchRecord.getBranchStatus().isAllowConfirm());
        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRMING, null);
    }

    @Override
    public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
        if (response == null || response.isBizSuccess()) {
            return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_SUCCESS, null);
        }
        return getRepository().updateTccBranchStatusAsync(branchRecord, TccStatus.CONFIRM_FAILED, null);
    }

    @Override
    public CompletionStage<Void> beginCancelAsync(boolean timeout) {
        Guard.checkArgument(branchRecord.getBranchStatus().isRollbackOnly());
        return getRepository().updateTccBranchStatusAsync(branchRecord,
                timeout ? TccStatus.BEFORE_TIMEOUT : TccStatus.CANCELLING, null);
    }

    @Override
    public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
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