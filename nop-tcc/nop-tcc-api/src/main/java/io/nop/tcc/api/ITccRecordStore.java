/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ITccRecordStore {
    ITccRecord newTccRecord(String txnGroup);

    ITccBranchRecord newBranchRecord(ITccRecord record, TccBranchRequest request);

    CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId);

    CompletionStage<List<ITccBranchRecord>> getBranchRecordsAsync(ITccRecord record);

    CompletionStage<Void> saveTccRecordAsync(ITccRecord record, TccStatus initStatus);

    CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error);

    CompletionStage<Void> saveBranchRecordAsync(ITccBranchRecord branchRecord, TccStatus initStatus);

    CompletionStage<Void> updateTccBranchStatusAsync(ITccBranchRecord record, TccStatus status, Throwable error);

    List<? extends ITccRecord> fetchExpiredRecords(int pageSize, long expireGap, long checkInterval, int maxRetryCount);

//    void forEachExpiredRecord(Function<ITccRecord, CompletionStage<Void>> consumer, long expireGap, int maxRetryCount,
//                              ICancelToken cancelToken);

    void removeCompletedRecords(long retentionTime, boolean onlyCompleted);

    /**
     * 耗尽行前置终态化（plan 2515 裁定⑧）：将 retryTimes>=maxRetryCount 且已过期且未完结的记录
     * 置为 KILLED 终态（endTime 落账），使其进入既有 finished 清理谓词——消除耗尽行的
     * 「恢复扫描排除 + 清理谓词排除」双重永久滞留。返回终态化的行数。
     *
     * @param maxRetryCount 重试次数上限（超过即视为耗尽）
     * @param batchSize     单批终态化上限（有界，防大表首扫冲击）
     */
    int killExhaustedRecords(int maxRetryCount, int batchSize);

    /**
     * {@link #removeCompletedRecords(long, boolean)} 的有界批删形态（plan 2515 裁定⑧）：
     * 按锚列 beginTime 拾取 + 单批删除上限，返回删除的记录行数（分支随父级联）。
     */
    int removeCompletedRecords(long retentionTime, boolean onlyCompleted, int batchSize);

}