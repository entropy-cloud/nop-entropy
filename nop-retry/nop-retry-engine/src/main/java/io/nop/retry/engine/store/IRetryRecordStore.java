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
import io.nop.retry.dao.entity.NopRetryAttempt;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;

import java.util.List;

public interface IRetryRecordStore {

    NopRetryRecord newRecord(IRetryTask task, ApiRequest<?> request);

    /**
     * 为记录创建新的执行尝试实体（attemptNo = 当前 retryCount + 1，status = WAITING）。
     * 执行开始后由引擎置为 RUNNING 并调用 {@link #saveAttempt} 落盘。
     */
    NopRetryAttempt newAttempt(NopRetryRecord record);

    /**
     * 保存执行尝试（插入或更新）。每次执行尝试（含立即重试与延迟重试）一条记录。
     */
    void saveAttempt(NopRetryAttempt attempt);
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
    NopRetryRecord findPendingRecordByIdempotentId(String namespaceId, String groupId, String idempotentId);

    /**
     * 根据幂等ID查找任意生命周期状态的重试记录（PENDING/RETRYING/COMPLETED/SUSPENDED）。
     * 提交去重必须覆盖全部状态：幂等键在记录行存在期间始终被占用，
     * 只查活跃状态会让终态记录的重提交绕过blockStrategy、直接撞唯一键（plan 2282 G7-14-01）。
     */
    NopRetryRecord findRecordByIdempotentId(String namespaceId, String groupId, String idempotentId);

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

    /**
     * 1.6-C7-001（plan 2502）：三账本表保留期清理。参数语义 =
     * ai-dev/design/concurrency/ledger-retention-policy.md §3.4 成员 1 行（时间锚列显式落名）：
     * <ul>
     * <li>COMPLETED record：锚列 updateTime、谓词 status=COMPLETED，级联删除其 attempt 行</li>
     * <li>死信：锚列 createTime，级联删除孤儿 attempt（recordId IN 死信 recordId 集；
     * sid 为 seq 生成不复用，删除安全）</li>
     * <li>孤儿 attempt 兜底扫描：锚列 startTime，recordId 不在任何活跃 record 行——统一覆盖
     * 死信迁移、OVERWRITE 重建（无死信行孤儿源）及历史遗留孤儿</li>
     * </ul>
     * 每轮每表先按锚列谓词+limit 有界拾取主键集再按 IN 集批删（单批删除上限 batchSize）；
     * 谓词幂等，重复触发无害。retentionTimeMillis &lt;= 0 或 batchSize &lt;= 0 时行为未定义，
     * 由调用方（引擎装配期校验）fail-fast 保证不出现。
     *
     * @param retentionTimeMillis 保留期（毫秒）
     * @param batchSize           单轮单表删除上限
     * @return 本轮删除的行数（record+attempt+deadLetter 合计）
     */
    long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize);

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
