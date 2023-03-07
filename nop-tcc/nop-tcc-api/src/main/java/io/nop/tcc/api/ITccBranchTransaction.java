/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tcc.api;

import io.nop.api.core.beans.ApiResponse;

import java.util.concurrent.CompletionStage;

public interface ITccBranchTransaction {
    default String getTxnGroup() {
        return getBranchRecord().getTxnGroup();
    }

    default String getTxnId() {
        return getBranchRecord().getTxnId();
    }

    default String getParentBranchId() {
        return getBranchRecord().getParentBranchId();
    }

    default String getBranchId() {
        return getBranchRecord().getBranchId();
    }

    default int getBranchNo() {
        return getBranchRecord().getBranchNo();
    }

    default TccStatus getBranchStatus() {
        return getBranchRecord().getBranchStatus();
    }

    ITccBranchRecord getBranchRecord();

    CompletionStage<Void> beginTryAsync();

    /**
     * 如果请求没有发送到服务端就失败，或者服务端明确返回false，则进入状态BRANCH_TRY_FAILED。 如果出现未知的异常，则进入状态BRANCH_TRY_UNKNOWN。
     * 如果服务端明确返回true，如果没有confirmMethod，则直接进入BRANCH_CONFIRMED，否则进入状态BRANCH_TRIED
     */
    CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex);

    CompletionStage<Void> beginConfirmAsync();

    CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex);

    CompletionStage<Void> beginCancelAsync(boolean timeout);

    CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex);
}
