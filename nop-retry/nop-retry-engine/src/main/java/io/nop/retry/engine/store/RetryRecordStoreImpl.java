/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.OrmEntityState;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryAttempt;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.dao.entity._gen._NopRetryAttempt;
import io.nop.retry.dao.entity._gen._NopRetryDeadLetter;
import io.nop.retry.engine.NopRetryConstants;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static io.nop.retry.dao.entity._gen._NopRetryRecord.*;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import java.util.ArrayList;
public class RetryRecordStoreImpl implements IRetryRecordStore {

    private static final Logger LOG = LoggerFactory.getLogger(RetryRecordStoreImpl.class);

    private static final List<Integer> PENDING_STATUSES = Arrays.asList(
            NopRetryConstants.RETRY_RECORD_STATUS_PENDING,
            NopRetryConstants.RETRY_RECORD_STATUS_RETRYING
    );

    /**
     * 默认命名空间ID
     */
    public static final String DEFAULT_NAMESPACE_ID = "default";

    /**
     * 默认分组ID
     */
    public static final String DEFAULT_GROUP_ID = "default";

    private IDaoProvider daoProvider;

    /**
     * 当前 store 的命名空间ID，用于多租户隔离
     */
    private String namespaceId = DEFAULT_NAMESPACE_ID;

    /**
     * 当前 store 的分组ID，用于逻辑分组
     */
    private String groupId = DEFAULT_GROUP_ID;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public String getGroupId() {
        return groupId;
    }
    @Override
    public NopRetryRecord newRecord(IRetryTask task, ApiRequest<?> request) {
        IOrmEntityDao<NopRetryRecord> dao = getRecordDao();

        NopRetryRecord record = dao.newEntity();
        
        // 设置命名空间和分组，优先使用 task 中的值
        record.setNamespaceId(task.getNamespaceId() != null ? task.getNamespaceId() : namespaceId);
        record.setGroupId(task.getGroupId() != null ? task.getGroupId() : groupId);
        
        record.setIdempotentId(task.getIdempotentId());
        record.setPolicyId(task.getPolicyId());
        record.setServiceName(task.getServiceName());
        record.setServiceMethod(task.getServiceMethod());
        record.setExecutorName(task.getExecutorName());
        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_PENDING);
        record.setRetryCount(0);

        NopRetryPolicy policy = loadPolicy(task.getPolicyId());
        if (policy != null && policy.getMaxRetryCount() != null) {
            record.setMaxRetryCount(policy.getMaxRetryCount());
        } else {
            record.setMaxRetryCount(NopRetryConstants.DEFAULT_MAX_RETRY_COUNT);
        }

        if (request != null) {
            record.setRequestPayload(JsonTool.serialize(request, false));
        }

        // 首提租约起点（plan 2515 裁定④钉改写登记：唯一允许的钉改写）。原钉「一次性推延一个
        // 租约周期」曾是首执行窗的唯一防线；现由续租协议替代为结构化契约——每轮 attempt 前
        // renewLease CAS 续租、0 行败方显式中止（ERR_RETRY_LEASE_LOST），扫描器接管面（含
        // 首执行窗）由协议闭合。推延值保留为首提租约的起点（nextTriggerTime = now + 租约），
        // 调用方失联超过租约后扫描器照常接管，双执行由续租 CAS + attempt 级超时上界闭合。
        record.setNextTriggerTime(new Timestamp(CoreMetrics.currentTimestamp().getTime()
                + NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS));

        // 确定性分区：同 (namespaceId, groupId, idempotentId) 稳定落同一分区，集群扫描按分区取数
        String partitionKey = (record.getNamespaceId() == null ? "" : record.getNamespaceId())
                + ":" + (record.getGroupId() == null ? "" : record.getGroupId())
                + ":" + (record.getIdempotentId() == null ? "" : record.getIdempotentId());
        record.setPartitionIndex(Math.floorMod(partitionKey.hashCode(), NopRetryConstants.DEFAULT_PARTITION_COUNT));
        return record;
    }

    @Override
    public NopRetryAttempt newAttempt(NopRetryRecord record) {
        IOrmEntityDao<NopRetryAttempt> dao = getAttemptDao();
        NopRetryAttempt attempt = dao.newEntity();
        attempt.setRecordId(record.getSid());
        // 1.6-C6-002（plan 2515 裁定⑦）：attemptNo 取该 record 名下 max(attemptNo)+1（对账后
        // 单行 max 查询）——崩溃/挂死后遗留 RUNNING attempt#1 在场时 scanner 重拾仍产出
        // attemptNo=2，UK_RETRY_ATTEMPT_RECORD_NO 碰撞从根消除（原 retryCount+1 形态在
        // retryCount 未递增的恢复路径必产出同号 attempt → 同步抛 UK → 记录永久砖化）。
        // 无 attempt 行时回退 retryCount+1（首提路径语义不变）。
        attempt.setAttemptNo(nextAttemptNo(record.getSid(), record.getRetryCount()));
        attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_WAITING);
        attempt.setStartTime(new Timestamp(getCurrentTime()));
        attempt.setRequestPayloadSnapshot(record.getRequestPayload());
        return attempt;
    }

    /**
     * 1.6-C6-002（plan 2515 裁定⑦）：attemptNo = max(attemptNo)+1；无行时回退 retryCount+1
     * （retryCount 亦缺失则 1）。
     */
    private int nextAttemptNo(String recordId, Integer fallbackRetryCount) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopRetryAttempt.PROP_NAME_recordId, recordId));
        query.addOrderField(_NopRetryAttempt.PROP_NAME_attemptNo, false);
        query.setLimit(1);
        NopRetryAttempt last = getAttemptDao().findFirstByQuery(query);
        if (last != null && last.getAttemptNo() != null) {
            return last.getAttemptNo() + 1;
        }
        return (fallbackRetryCount != null ? fallbackRetryCount : 0) + 1;
    }

    @Override
    public void saveAttempt(NopRetryAttempt attempt) {
        // 首次保存为 TRANSIENT 插入；执行完成后再次保存时实体已 MANAGED，需走更新
        if (attempt.orm_state().isTransient()) {
            try {
                getAttemptDao().saveEntityDirectly(attempt);
            } catch (Exception e) {
                if (!isDuplicateKeyError(e)) {
                    throw e;
                }
                // 1.6-C6-002（plan 2515 裁定⑦）：首插 duplicate-key 防御兜底（与 record 侧
                // resolveInsertRaceWinner 对称形态）——对账关闭遗留 RUNNING 行后以 max+1
                // 重插一次；兜底触发即 WARN 显式留痕，不静默。
                LOG.warn("nop.retry.attempt.insert-conflict:recordId={},attemptNo={}",
                        attempt.getRecordId(), attempt.getAttemptNo(), e);
                reconcileStaleRunningAttempts(attempt.getRecordId());
                attempt.setAttemptNo(nextAttemptNo(attempt.getRecordId(), null));
                // 首插失败后实体滞留 SAVING 状态（session 异常路径不复位），重置为 TRANSIENT
                // 才能按新 attemptNo 重新走插入状态机（同一 session 每次调用独立开启，
                // 失败插入未落库，主键复用安全）
                attempt.orm_state(OrmEntityState.TRANSIENT);
                getAttemptDao().saveEntityDirectly(attempt);
            }
        } else {
            getAttemptDao().updateEntityDirectly(attempt);
        }
    }

    @Override
    public void reconcileStaleRunningAttempts(String recordId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopRetryAttempt.PROP_NAME_recordId, recordId));
        query.addFilter(FilterBeans.eq(_NopRetryAttempt.PROP_NAME_status,
                NopRetryConstants.RETRY_ATTEMPT_STATUS_RUNNING));
        List<NopRetryAttempt> staleAttempts = getAttemptDao().findAllByQuery(query);
        if (staleAttempts.isEmpty()) {
            return;
        }
        // 崩溃/挂死恢复语义：遗留 RUNNING attempt 置 STOPPED（KILLED 类终态，激活既有
        // 零使用常量）+ endTime 落账——僵尸 RUNNING 行终态化，UK 碰撞砖化链不可达
        Timestamp endTime = new Timestamp(getCurrentTime());
        for (NopRetryAttempt stale : staleAttempts) {
            stale.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_STOPPED);
            stale.setEndTime(endTime);
            getAttemptDao().updateEntityDirectly(stale);
        }
        LOG.warn("nop.retry.attempt.stale-reconciled:recordId={},count={}",
                recordId, staleAttempts.size());
    }

    private static boolean isDuplicateKeyError(Throwable e) {
        return e instanceof NopException
                && DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(((NopException) e).getErrorCode());
    }

    @Override
    public List<NopRetryRecord> fetchPendingRecords(int limit, IntRangeSet partitions) {
        IOrmEntityDao<NopRetryRecord> dao = getRecordDao();
        long now = dao.getDbEstimatedClock().getMinCurrentTimeMillis();

        QueryBean query = new QueryBean();
        query.setLimit(limit);

        // 1.6-C7-012（plan 2515 裁定⑨）：移除 eq(namespaceId) 单值硬过滤——store 单值装配
        // （namespaceId 普通 setter，无分片装配）造成的意外收窄曾使非 default 记录失败进入
        // PENDING 后扫描器永远看不到（重试承诺静默违约）。租户隔离由分区键
        // （namespaceId:groupId:idempotentId → partitionIndex）与幂等 UK 承担；
        // namespaceId 字段保留为提交缺省值用途（findRecordByIdempotentId 等显式传参路径零改写）。

        // 查询 nextTriggerTime <= now 的 PENDING/RETRYING 记录
        query.addFilter(FilterBeans.in(PROP_NAME_status, PENDING_STATUSES));
        query.addFilter(FilterBeans.le(PROP_NAME_nextTriggerTime, now));

        // 使用区间查询过滤分区
        if (partitions != null && !partitions.isEmpty()) {
            List<TreeBean> rangeFilters = new ArrayList<>();
            for (IntRangeBean range : partitions.getRanges()) {
                rangeFilters.add(FilterBeans.between(PROP_NAME_partitionIndex,
                        range.getOffset(), range.getLast()));
            }
            query.addFilter(FilterBeans.or(rangeFilters));
        }

        query.addOrderField(PROP_NAME_nextTriggerTime, false);
        query.addOrderField(PROP_NAME_sid, false);

        return dao.findAllByQuery(query);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public List<NopRetryRecord> tryLockRecordsForProcess(List<NopRetryRecord> records, long retryingTimeoutMs) {
        if (records == null || records.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        IOrmEntityDao<NopRetryRecord> dao = getRecordDao();
        long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();

        // 更新所有记录：
        // - PENDING → RETRYING
        // - 设置 nextTriggerTime = now + retryingTimeoutMs（作为锁过期时间）
        for (NopRetryRecord record : records) {
            Integer currentStatus = record.getStatus();
            if (currentStatus != null && currentStatus == NopRetryConstants.RETRY_RECORD_STATUS_PENDING) {
                record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_RETRYING);
            }
            record.setNextTriggerTime(new Timestamp(now + retryingTimeoutMs));
        }

        // 通过乐观锁批量更新，返回实际成功更新的记录
        return dao.tryUpdateManyWithVersionCheck(records);
    }

    @Override
    public NopRetryRecord loadRecord(String recordId) {
        return getRecordDao().requireEntityById(recordId);
    }

    @Override
    public NopRetryRecord findPendingRecordByIdempotentId(String namespaceId, String groupId, String idempotentId) {
        if (idempotentId == null || idempotentId.isEmpty()) {
            return null;
        }

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_namespaceId,
                namespaceId != null ? namespaceId : this.namespaceId));
        query.addFilter(FilterBeans.eq(PROP_NAME_groupId,
                groupId != null ? groupId : this.groupId));
        query.addFilter(FilterBeans.eq(PROP_NAME_idempotentId, idempotentId));
        query.addFilter(FilterBeans.in(PROP_NAME_status, PENDING_STATUSES));

        return getRecordDao().findFirstByQuery(query);
    }

    @Override
    public NopRetryRecord findRecordByIdempotentId(String namespaceId, String groupId, String idempotentId) {
        if (idempotentId == null || idempotentId.isEmpty()) {
            return null;
        }

        // 不带状态过滤：幂等键去重必须覆盖全部生命周期状态（plan 2282 G7-14-01）
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_namespaceId,
                namespaceId != null ? namespaceId : this.namespaceId));
        query.addFilter(FilterBeans.eq(PROP_NAME_groupId,
                groupId != null ? groupId : this.groupId));
        query.addFilter(FilterBeans.eq(PROP_NAME_idempotentId, idempotentId));

        return getRecordDao().findFirstByQuery(query);
    }

    @Override
    public void deleteRecord(NopRetryRecord record) {
        getRecordDao().deleteEntityDirectly(record);
    }

    /**
     * 1.6-C6-003（plan 2515 裁定⑤）：OVERWRITE 原子替换包络。REQUIRED 同事务内 delete+save，
     * 任何失败整体回滚（旧记录存活）。直接使用 dao 原语，不经 {@link #deleteRecord}（裸自动提交）
     * 与 {@link #saveRecord}（REQUIRES_NEW）——方法体内自调用会绕事务拦截器且传播分叉。
     */
    @Transactional
    @Override
    public void replaceRecord(NopRetryRecord existingRecord, NopRetryRecord newRecord) {
        getRecordDao().deleteEntityDirectly(existingRecord);
        getRecordDao().saveEntityDirectly(newRecord);
    }

    /**
     * 1.6-C3-003（plan 2515 裁定④）：续租 CAS。载体 = 既有乐观锁 tryUpdateWithVersionCheck；
     * 0 行（实体被标记 readonly）= 租约被扫描器/他链抢持，败方由引擎显式中止。
     */
    @Override
    public boolean renewLease(NopRetryRecord record, long leaseTimeoutMs) {
        record.setNextTriggerTime(new Timestamp(getCurrentTime() + leaseTimeoutMs));
        return getRecordDao().tryUpdateWithVersionCheck(record);
    }

    @Override
    public NopRetryDeadLetter loadDeadLetter(String deadLetterId) {
        return getDeadLetterDao().getEntityById(deadLetterId);
    }

    @Override
    public NopRetryPolicy loadPolicy(String policyId) {
        if (policyId == null || policyId.isEmpty()) {
            return null;
        }
        return getPolicyDao().requireEntityById(policyId);
    }

    @Override
    public void savePolicy(NopRetryPolicy policy) {
        getPolicyDao().saveEntityDirectly(policy);
    }

    @Override
    public void saveDeadLetter(NopRetryDeadLetter deadLetter) {
        getDeadLetterDao().saveEntityDirectly(deadLetter);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void saveRecord(NopRetryRecord record) {
        getRecordDao().saveEntityDirectly(record);
    }

    @Override
    public void updateRecord(NopRetryRecord record) {
        getRecordDao().updateEntityDirectly(record);
    }

    @Transactional
    @Override
    public void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage, String errorStack) {
        IEntityDao<NopRetryDeadLetter> deadLetterDao = getDeadLetterDao();
        NopRetryDeadLetter deadLetter = deadLetterDao.newEntity();
        deadLetter.setNamespaceId(record.getNamespaceId());
        deadLetter.setGroupId(record.getGroupId());
        deadLetter.setRecordId(record.getSid());
        deadLetter.setPolicyId(record.getPolicyId());
        deadLetter.setIdempotentId(record.getIdempotentId());
        deadLetter.setExecutorName(record.getExecutorName());
        deadLetter.setServiceName(record.getServiceName());
        deadLetter.setServiceMethod(record.getServiceMethod());
        deadLetter.setRequestPayload(record.getRequestPayload());
        deadLetter.setFailureCode(errorCode);
        deadLetter.setFailureMessage(errorMessage);
        deadLetter.setErrorStack(errorStack);
        deadLetter.setFinalStatus(record.getStatus());

        deadLetterDao.saveEntityDirectly(deadLetter);

        // 死信保存成功后删除原 record 行，使同一 idempotentId 可以重新提交（plan 342 裁定）。
        // 副作用：deadLetter.record 关系悬空（无 FK/cascade），attempt 行成为孤儿；
        // 终态视图以死信页为准（死信行保留 recordId 快照）。
        getRecordDao().deleteEntityDirectly(record);
    }

    @Override
    public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
        // 清理谓词时间锚列显式落名（ledger-retention-policy §3.4 成员 1）：
        // nop_retry_record=updateTime(+status=COMPLETED)、nop_retry_dead_letter=createTime、
        // nop_retry_attempt=recordId 级联随父（死信 recordId 集）+ startTime 孤儿兜底扫描。
        long now = getRecordDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp cutoff = new Timestamp(now - retentionTimeMillis);
        long total = 0L;

        // ① COMPLETED record：updateTime < cutoff 且 status=COMPLETED，级联删 attempt
        QueryBean recordQuery = new QueryBean();
        recordQuery.setLimit(batchSize);
        recordQuery.addFilter(FilterBeans.eq(PROP_NAME_status, NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED));
        recordQuery.addFilter(FilterBeans.lt(PROP_NAME_updateTime, cutoff));
        List<NopRetryRecord> expiredRecords = getRecordDao().findAllByQuery(recordQuery);
        if (!expiredRecords.isEmpty()) {
            List<String> recordIds = new ArrayList<>(expiredRecords.size());
            for (NopRetryRecord expired : expiredRecords) {
                recordIds.add(expired.getSid());
            }
            total += deleteAttemptsByRecordIds(recordIds);
            total += deleteRecordsBySids(recordIds);
        }

        // ② 死信：createTime < cutoff，级联删孤儿 attempt（recordId IN 死信 recordId 集）。
        // 死信化删除 record 后 attempt 悬空（无 FK/cascade），随死信保留期一并清理。
        QueryBean deadLetterQuery = new QueryBean();
        deadLetterQuery.setLimit(batchSize);
        deadLetterQuery.addFilter(FilterBeans.lt(_NopRetryDeadLetter.PROP_NAME_createTime, cutoff));
        List<NopRetryDeadLetter> expiredDeadLetters = getDeadLetterDao().findAllByQuery(deadLetterQuery);
        if (!expiredDeadLetters.isEmpty()) {
            List<String> orphanRecordIds = new ArrayList<>(expiredDeadLetters.size());
            List<String> deadLetterIds = new ArrayList<>(expiredDeadLetters.size());
            for (NopRetryDeadLetter expired : expiredDeadLetters) {
                deadLetterIds.add(expired.getSid());
                if (expired.getRecordId() != null) {
                    orphanRecordIds.add(expired.getRecordId());
                }
            }
            if (!orphanRecordIds.isEmpty()) {
                total += deleteAttemptsByRecordIds(orphanRecordIds);
            }

            QueryBean deadLetterDelete = new QueryBean();
            deadLetterDelete.addFilter(FilterBeans.in(_NopRetryDeadLetter.PROP_NAME_sid, deadLetterIds));
            total += getDeadLetterDao().deleteByQuery(deadLetterDelete);
        }

        // ③ 孤儿 attempt 兜底扫描（锚列 startTime）：recordId 不在任何活跃 record 行——
        // 统一覆盖死信迁移、OVERWRITE 重建（无死信行的孤儿源）及历史遗留孤儿；批内有界拾取，
        // 存在性核对后批删，谓词幂等（存留 attempt 的 record 必在活跃集，不误伤）。
        QueryBean attemptScanQuery = new QueryBean();
        attemptScanQuery.setLimit(batchSize);
        attemptScanQuery.addFilter(FilterBeans.lt(_NopRetryAttempt.PROP_NAME_startTime, cutoff));
        List<NopRetryAttempt> oldAttempts = getAttemptDao().findAllByQuery(attemptScanQuery);
        if (!oldAttempts.isEmpty()) {
            List<String> candidateRecordIds = new ArrayList<>();
            for (NopRetryAttempt attempt : oldAttempts) {
                if (attempt.getRecordId() != null && !candidateRecordIds.contains(attempt.getRecordId())) {
                    candidateRecordIds.add(attempt.getRecordId());
                }
            }
            if (!candidateRecordIds.isEmpty()) {
                QueryBean liveQuery = new QueryBean();
                liveQuery.addFilter(FilterBeans.in(PROP_NAME_sid, candidateRecordIds));
                List<NopRetryRecord> liveRecords = getRecordDao().findAllByQuery(liveQuery);
                List<String> orphanRecordIds = new ArrayList<>(candidateRecordIds);
                for (NopRetryRecord live : liveRecords) {
                    orphanRecordIds.remove(live.getSid());
                }
                if (!orphanRecordIds.isEmpty()) {
                    total += deleteAttemptsByRecordIds(orphanRecordIds);
                }
            }
        }
        return total;
    }

    private long deleteAttemptsByRecordIds(List<String> recordIds) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(_NopRetryAttempt.PROP_NAME_recordId, recordIds));
        return getAttemptDao().deleteByQuery(query);
    }

    private long deleteRecordsBySids(List<String> sids) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(PROP_NAME_sid, sids));
        return getRecordDao().deleteByQuery(query);
    }

    @Override
    public long getCurrentTime() {
        return getRecordDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
    }

    private IOrmEntityDao<NopRetryRecord> getRecordDao() {
        return (IOrmEntityDao<NopRetryRecord>) daoProvider.daoFor(NopRetryRecord.class);
    }

    private IEntityDao<NopRetryDeadLetter> getDeadLetterDao() {
        return daoProvider.daoFor(NopRetryDeadLetter.class);
    }

    private IEntityDao<NopRetryPolicy> getPolicyDao() {
        return daoProvider.daoFor(NopRetryPolicy.class);
    }

    private IOrmEntityDao<NopRetryAttempt> getAttemptDao() {
        return (IOrmEntityDao<NopRetryAttempt>) daoProvider.daoFor(NopRetryAttempt.class);
    }
}
