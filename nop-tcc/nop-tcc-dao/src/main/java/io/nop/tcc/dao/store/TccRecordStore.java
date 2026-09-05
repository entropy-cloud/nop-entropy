/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.dao.store;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.tcc.api.*;
import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.dao.entity.NopTccRecord;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class TccRecordStore implements ITccRecordStore {
    static final Logger LOG = LoggerFactory.getLogger(TccRecordStore.class);

    private IDaoProvider daoProvider;

    private int defaultBranchTimeout;

    private int defaultTxnTimeout;

    private int defaultMaxRetryTimes;

    @InjectValue("@cfg:nop.tcc.default-branch-timeout-ms|10000")
    public void setDefaultBranchTimeout(int defaultBranchTimeoutMs) {
        this.defaultBranchTimeout = defaultBranchTimeoutMs;
    }

    @InjectValue("@cfg:nop.tcc.default-txn-timeout-ms|60000")
    public void setDefaultTxnTimeout(int defaultTxnTimeoutMs) {
        this.defaultTxnTimeout = defaultTxnTimeoutMs;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @InjectValue("@cfg:nop.tcc.default-max-retry-times|100")
    public void setDefaultMaxRetryTimes(int defaultMaxRetryTimes) {
        this.defaultMaxRetryTimes = defaultMaxRetryTimes;
    }

    private IOrmEntityDao<NopTccRecord> recordDao() {
        return (IOrmEntityDao<NopTccRecord>) daoProvider.daoFor(NopTccRecord.class);
    }

    private IEntityDao<NopTccBranchRecord> branchDao() {
        return daoProvider.daoFor(NopTccBranchRecord.class);
    }

    @Override
    public ITccRecord newTccRecord(String txnGroup) {
        IEntityDao<NopTccRecord> dao = recordDao();

        NopTccRecord record = dao.newEntity();
        record.setTxnGroup(txnGroup);
        record.setAppId(AppConfig.appName());
        Timestamp beginTime = CoreMetrics.currentTimestamp();
        record.setBeginTime(beginTime);
        record.setExpireTime(new Timestamp(beginTime.getTime() + defaultTxnTimeout));
        record.setStatus(TccStatus.CREATED.getCode());
        dao.initEntityId(record);
        return record;
    }

    @Override
    public ITccBranchRecord newBranchRecord(ITccRecord record, TccBranchRequest request) {
        IEntityDao<NopTccBranchRecord> dao = branchDao();

        NopTccBranchRecord branchRecord = dao.newEntity();
        branchRecord.setTxnId(record.getTxnId());
        branchRecord.setBeginTime(CoreMetrics.currentTimestamp());
        branchRecord.setCancelMethod(request.getCancelMethod());
        branchRecord.setConfirmMethod(request.getConfirmMethod());
        branchRecord.setServiceMethod(request.getServiceMethod());
        branchRecord.setServiceName(request.getServiceName());
        branchRecord.setStatus(TccStatus.CREATED.getCode());
        branchRecord.setParentBranchId(request.getParentBranchId());
        branchRecord.setBranchNo(request.getParentBranchNo() + 1);
        branchRecord.setMaxRetryTimes(defaultMaxRetryTimes);
        dao.initEntityId(branchRecord);

        // 将TCC事务上下文头写入请求快照：confirm/cancel重放基于该快照发起调用，
        // 缺少txnId/branchId头时参与者无法定位预留记录实现幂等控制。
        // 必须在initEntityId之后执行，否则branchId头为空
        ApiRequest<?> apiRequest = request.getRequest();
        if (apiRequest != null) {
            ApiHeaders.setTxnGroup(apiRequest, record.getTxnGroup());
            ApiHeaders.setTxnId(apiRequest, record.getTxnId());
            ApiHeaders.setTxnBranchId(apiRequest, branchRecord.getBranchId());
            ApiHeaders.setTxnBranchNo(apiRequest, branchRecord.getBranchNo());
            branchRecord.setRequestData(JsonTool.stringify(apiRequest));
        }

        return branchRecord;
    }

    @Override
    public CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId) {
        return FutureHelper.futureCall(() -> {
            NopTccRecord record = recordDao().getEntityById(txnId);
            // txnId为主键全局唯一，但按组加载的契约要求txnGroup匹配：
            // 错误的txnGroup静默加载到别组事务会导致补偿操作跨组误操作
            if (record != null && !Objects.equals(record.getTxnGroup(), txnGroup))
                return null;
            return record;
        });
    }

    @Override
    public CompletionStage<List<ITccBranchRecord>> getBranchRecordsAsync(ITccRecord record) {
        return FutureHelper.futureCall(() -> {
            NopTccBranchRecord example = new NopTccBranchRecord();
            example.setTxnId(record.getTxnId());
            return branchDao().findAllByExample(example);
        });
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public CompletionStage<Void> saveTccRecordAsync(ITccRecord record, TccStatus initStatus) {
        return FutureHelper.futureCall(() -> {
            NopTccRecord tccRecord = (NopTccRecord) record;
            tccRecord.setStatus(initStatus.getCode());
            recordDao().saveEntityDirectly(tccRecord);
            return null;
        });
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
        return FutureHelper.futureCall(() -> {
            NopTccRecord tccRecord = (NopTccRecord) record;
            // from状态必须在修改内存status之前捕获，updateByQuery按[id+from状态]做单语句CAS：
            // 并发writer/stale writer导致DB状态已变化时本次更新0行，跳过以保护已落库的终态
            Integer fromStatus = tccRecord.getStatus();

            Map<String, Object> props = new HashMap<>();
            props.put(NopTccRecord.PROP_NAME_status, status.getCode());

            if (status.isFinished()) {
                Timestamp endTime = CoreMetrics.currentTimestamp();
                props.put(NopTccRecord.PROP_NAME_endTime, endTime);
                tccRecord.setEndTime(endTime);
            }

            if (error != null) {
                ErrorBean errorBean = getErrorBean(error);
                props.put(NopTccRecord.PROP_NAME_errorStack, errorBean.getErrorStack());
                props.put(NopTccRecord.PROP_NAME_errorCode, errorBean.getErrorCode());
                props.put(NopTccRecord.PROP_NAME_errorMessage, errorBean.getDescription());
                tccRecord.setErrorStack(errorBean.getErrorStack());
                tccRecord.setErrorCode(errorBean.getErrorCode());
                tccRecord.setErrorMessage(errorBean.getDescription());
            }

            long updated = recordDao().updateByQuery(statusCasQuery(tccRecord.getTxnId(), fromStatus), props);
            if (updated == 0) {
                LOG.warn("nop.tcc.skip-status-update-due-to-conflict:txnId={},fromStatus={},toStatus={}",
                        tccRecord.getTxnId(), fromStatus, status.getCode());
                return null;
            }
            tccRecord.setStatus(status.getCode());
            return null;
        });
    }

    private QueryBean statusCasQuery(String txnId, Integer fromStatus) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopTccRecord.PROP_NAME_txnId, txnId));
        query.addFilter(FilterBeans.eq(NopTccRecord.PROP_NAME_status, fromStatus));
        return query;
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public CompletionStage<Void> saveBranchRecordAsync(ITccBranchRecord branchRecord, TccStatus initStatus) {
        return FutureHelper.futureCall(() -> {
            NopTccBranchRecord record = (NopTccBranchRecord) branchRecord;
            record.setStatus(initStatus.getCode());
            record.setBeginTime(CoreMetrics.currentTimestamp());
            if (record.getExpireTime() == null)
                record.setExpireTime(new Timestamp(record.getBeginTime().getTime() + defaultBranchTimeout));
            branchDao().saveEntityDirectly(record);
            return null;
        });
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public CompletionStage<Void> updateTccBranchStatusAsync(ITccBranchRecord branchRecord, TccStatus status, Throwable error) {
        return FutureHelper.futureCall(() -> {
            NopTccBranchRecord record = (NopTccBranchRecord) branchRecord;
            // from状态先于内存修改捕获，updateByQuery按[branchId+from状态]做单语句CAS，终态不被stale writer覆盖
            Integer fromStatus = record.getStatus();

            Map<String, Object> props = new HashMap<>();
            props.put(NopTccBranchRecord.PROP_NAME_status, status.getCode());

            // 补偿尝试计数：每次进入CONFIRMING/CANCELLING/BEFORE_TIMEOUT视为一次补偿尝试
            if (status == TccStatus.CONFIRMING || status == TccStatus.CANCELLING || status == TccStatus.BEFORE_TIMEOUT) {
                Integer retryTimes = (record.getRetryTimes() == null ? 0 : record.getRetryTimes()) + 1;
                props.put(NopTccBranchRecord.PROP_NAME_retryTimes, retryTimes);
                record.setRetryTimes(retryTimes);
            }

            if (status.isFinished()) {
                Timestamp endTime = CoreMetrics.currentTimestamp();
                props.put(NopTccBranchRecord.PROP_NAME_endTime, endTime);
                record.setEndTime(endTime);
            }

            if (error != null) {
                ErrorBean errorBean = getErrorBean(error);
                if (status == TccStatus.CANCEL_FAILED) {
                    props.put(NopTccBranchRecord.PROP_NAME_cancelErrorStack, errorBean.getErrorStack());
                    props.put(NopTccBranchRecord.PROP_NAME_cancelErrorCode, errorBean.getErrorCode());
                    props.put(NopTccBranchRecord.PROP_NAME_cancelErrorMessage, errorBean.getDescription());
                    record.setCancelErrorStack(errorBean.getErrorStack());
                    record.setCancelErrorCode(errorBean.getErrorCode());
                    record.setCancelErrorMessage(errorBean.getDescription());
                } else if (status == TccStatus.CONFIRM_FAILED) {
                    props.put(NopTccBranchRecord.PROP_NAME_commitErrorStack, errorBean.getErrorStack());
                    props.put(NopTccBranchRecord.PROP_NAME_commitErrorCode, errorBean.getErrorCode());
                    props.put(NopTccBranchRecord.PROP_NAME_commitErrorMessage, errorBean.getDescription());
                    record.setCommitErrorStack(errorBean.getErrorStack());
                    record.setCommitErrorCode(errorBean.getErrorCode());
                    record.setCommitErrorMessage(errorBean.getDescription());
                } else {
                    props.put(NopTccBranchRecord.PROP_NAME_errorStack, errorBean.getErrorStack());
                    props.put(NopTccBranchRecord.PROP_NAME_errorCode, errorBean.getErrorCode());
                    props.put(NopTccBranchRecord.PROP_NAME_errorMessage, errorBean.getDescription());
                    record.setErrorStack(errorBean.getErrorStack());
                    record.setErrorCode(errorBean.getErrorCode());
                    record.setErrorMessage(errorBean.getDescription());
                }
            }

            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(NopTccBranchRecord.PROP_NAME_branchId, record.getBranchId()));
            query.addFilter(FilterBeans.eq(NopTccBranchRecord.PROP_NAME_status, fromStatus));

            long updated = branchDao().updateByQuery(query, props);
            if (updated == 0) {
                LOG.warn("nop.tcc.skip-branch-status-update-due-to-conflict:txnId={},branchId={},fromStatus={},toStatus={}",
                        record.getTxnId(), record.getBranchId(), fromStatus, status.getCode());
                return null;
            }
            record.setStatus(status.getCode());
            return null;
        });
    }

    protected ErrorBean getErrorBean(Throwable error) {
        return ErrorMessageManager.instance().buildErrorMessage(null, error);
    }

    @Override
    @Transactional
    public List<NopTccRecord> fetchExpiredRecords(int pageSize, long expireGap, long checkInterval, int maxRetryCount) {
        IOrmEntityDao<NopTccRecord> dao = recordDao();

        Timestamp minTime = new Timestamp(getCurrentTime(dao) - expireGap);
        Timestamp nextCheckTime = new Timestamp(minTime.getTime() + checkInterval);

        QueryBean query = new QueryBean();
        // 已经超时
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_expireTime, minTime));
        // 状态为还没有结束
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
        // 重试次数未超过上限，超过上限的事务会被忽略，不再处理
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
        query.setLimit(pageSize);
        query.addOrderField(NopTccRecord.PROP_NAME_beginTime, true);

        for (int i = 0; i < 100; i++) {
            List<NopTccRecord> records = dao.findPageByQuery(query);
            if (records.isEmpty())
                return records;

            // 更新超时时间为下一次检查时间，并递增重试次数
            for (NopTccRecord record : records) {
                record.setExpireTime(nextCheckTime);
                record.setRetryTimes((record.getRetryTimes() == null ? 0 : record.getRetryTimes()) + 1);
            }

            // 如果更新结果为空，则表示有其他线程也在扫描，并且已经处理这些记录
            List<NopTccRecord> ret = dao.tryUpdateManyWithVersionCheck(records);
            if (!ret.isEmpty())
                return ret;
        }

        // 如果一直没有找到符合条件的记录，则返回空
        return Collections.emptyList();
    }

    protected long getCurrentTime(IOrmEntityDao<NopTccRecord> dao) {
        return CoreMetrics.currentTimeMillis();
    }

    @Override
    @Transactional
    public void removeCompletedRecords(long retentionTime, boolean onlyCompleted) {
        QueryBean query = new QueryBean();
        Timestamp minTime = new Timestamp(CoreMetrics.currentTimeMillis() - retentionTime);
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_beginTime, minTime));
        if (onlyCompleted)
            query.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_status, TccStatus.getFinishedStatus()));

        // 分支记录随父记录生命周期清理：按已完结父记录的txnId集合删除分支。
        // 此前分支按自身status+beginTime独立删除，父已删而分支非终态时（如BIZ_CANCEL_FAILED
        // 全局配CANCEL_FAILED分支）产生永不清理的孤儿分支
        List<NopTccRecord> records = recordDao().findAllByQuery(query);
        if (!records.isEmpty()) {
            List<String> txnIds = new ArrayList<>(records.size());
            for (NopTccRecord record : records) {
                txnIds.add(record.getTxnId());
            }
            QueryBean branchQuery = new QueryBean();
            branchQuery.addFilter(FilterBeans.in(NopTccBranchRecord.PROP_NAME_txnId, txnIds));
            branchDao().deleteByQuery(branchQuery);
        }
        recordDao().deleteByQuery(query);
    }
}
