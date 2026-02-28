/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.store;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;

import java.util.List;

public interface IRetryRecordStore {

    NopRetryRecord newRecord(IRetryTask task, ApiRequest<?> request);
    /**
     * 获取待处理的重试记录（不锁定）。
     * <p>
     * 查询 nextTriggerTime <= now 的 PENDING/RETRYING 记录。
     *
     * @param limit      最大获取数量
     * @param partitions 分区范围（集群模式下使用），null 表示不限制
     * @return 待处理的记录列表，可能为空
     */
    List<NopRetryRecord> fetchPendingRecords(int limit, IntRangeSet partitions);

    /**
     * 批量锁定待处理的记录。
     * <p>
     * 通过乐观锁实现：
     * - PENDING → RETRYING
     * - 设置 nextTriggerTime = now + retryingTimeoutMs（作为锁过期时间）
     *
     * @param records           待锁定的记录列表
     * @param retryingTimeoutMs 锁超时时间
     * @return 实际锁定成功的记录列表，可能为空（并发竞争时）
     */
    List<NopRetryRecord> tryLockRecordsForProcess(List<NopRetryRecord> records, long retryingTimeoutMs);

    NopRetryRecord loadRecord(String recordId);

    /**
     * 根据幂等ID查找未完成的重试记录
     */
    NopRetryRecord findPendingRecordByIdempotentId(String idempotentId);

    /**
     * 删除未完成的重试记录
     */
    void deleteRecord(NopRetryRecord record);

    NopRetryDeadLetter loadDeadLetter(String deadLetterId);

    /**
     * 保存死信记录
     */
    void saveDeadLetter(NopRetryDeadLetter deadLetter);

    void saveRecord(NopRetryRecord record);

    void updateRecord(NopRetryRecord record);

    void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage, String errorStack);


    long getCurrentTime();

    /**
     * 保存重试策略
     */
    void savePolicy(NopRetryPolicy policy);

    /**
     * 加载重试策略
     */
    NopRetryPolicy loadPolicy(String policyId);
}
