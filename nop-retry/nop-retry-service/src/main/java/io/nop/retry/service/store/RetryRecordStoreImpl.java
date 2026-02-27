/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.service.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.service.NopRetryConstants;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static io.nop.retry.dao.entity._gen._NopRetryRecord.*;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import java.util.ArrayList;
public class RetryRecordStoreImpl implements IRetryRecordStore {

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
        record.setExecutorName(task.getExecutorId());
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

        record.setNextTriggerTime(CoreMetrics.currentTimestamp());
        return record;
    }

    @Override
    public List<NopRetryRecord> fetchPendingRecords(int limit, IntRangeSet partitions) {
        IOrmEntityDao<NopRetryRecord> dao = getRecordDao();
        long now = dao.getDbEstimatedClock().getMinCurrentTimeMillis();

        QueryBean query = new QueryBean();
        query.setLimit(limit);

        // 自动过滤命名空间
        query.addFilter(FilterBeans.eq(PROP_NAME_namespaceId, namespaceId));
        
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
    public NopRetryRecord findPendingRecordByIdempotentId(String idempotentId) {
        if (idempotentId == null || idempotentId.isEmpty()) {
            return null;
        }

        QueryBean query = new QueryBean();
        // 自动过滤命名空间
        query.addFilter(FilterBeans.eq(PROP_NAME_namespaceId, namespaceId));
        query.addFilter(FilterBeans.eq(PROP_NAME_idempotentId, idempotentId));
        query.addFilter(FilterBeans.in(PROP_NAME_status, PENDING_STATUSES));
        query.setLimit(1);

        List<NopRetryRecord> records = getRecordDao().findAllByQuery(query);
        return records.isEmpty() ? null : records.get(0);
    }

    @Override
    public void deleteRecord(NopRetryRecord record) {
        getRecordDao().deleteEntityDirectly(record);
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
        deadLetter.setBizNo(record.getBizNo());
        deadLetter.setExecutorName(record.getExecutorName());
        deadLetter.setServiceName(record.getServiceName());
        deadLetter.setServiceMethod(record.getServiceMethod());
        deadLetter.setRequestPayload(record.getRequestPayload());
        deadLetter.setFailureCode(errorCode);
        deadLetter.setFailureMessage(errorMessage);
        deadLetter.setErrorStack(errorStack);
        deadLetter.setFinalStatus(record.getStatus());

        deadLetterDao.saveEntityDirectly(deadLetter);

        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_MAX_RETRIES);
        getRecordDao().updateEntityDirectly(record);
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
}
