/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import io.nop.api.core.util.ICancelToken;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface ITccRecordRepository {
    ITccRecord newTccRecord(String txnGroup);

    ITccBranchRecord newBranchRecord(ITccRecord record, TccBranchRequest request);

    CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId);

    CompletionStage<List<ITccBranchRecord>> getBranchRecordsAsync(ITccRecord record);

    CompletionStage<Void> saveTccRecordAsync(ITccRecord record, TccStatus initStatus);

    CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error);

    CompletionStage<Void> saveBranchRecordAsync(ITccBranchRecord branchRecord, TccStatus initStatus);

    CompletionStage<Void> updateTccBranchStatusAsync(ITccBranchRecord record, TccStatus status, Throwable error);

    void forEachExpiredRecord(Function<ITccRecord, CompletionStage<Void>> consumer, long expireGap, int maxRetryCount,
                              ICancelToken cancelToken);

    void removeCompletedRecords(long retentionTime);

}