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

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class TccRecordStore implements ITccRecordStore {

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
            tccRecord.setStatus(status.getCode());

            if (status.isFinished())
                tccRecord.setEndTime(CoreMetrics.currentTimestamp());

            if (error != null) {
                ErrorBean errorBean = getErrorBean(error);
                tccRecord.setErrorStack(errorBean.getErrorStack());
                tccRecord.setErrorCode(errorBean.getErrorCode());
                tccRecord.setErrorMessage(errorBean.getDescription());
            }
            recordDao().updateEntityDirectly(tccRecord);
            return null;
        });
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
            record.setStatus(status.getCode());

            if (status.isFinished())
                record.setEndTime(CoreMetrics.currentTimestamp());

            if (error != null) {
                ErrorBean errorBean = getErrorBean(error);
                if (status == TccStatus.CANCEL_FAILED) {
                    record.setCancelErrorStack(errorBean.getErrorStack());
                    record.setCancelErrorCode(errorBean.getErrorCode());
                    record.setCancelErrorMessage(errorBean.getDescription());
                } else if (status == TccStatus.CONFIRM_FAILED) {
                    record.setCommitErrorStack(errorBean.getErrorStack());
                    record.setCommitErrorCode(errorBean.getErrorCode());
                    record.setCommitErrorMessage(errorBean.getDescription());
                } else {
                    record.setErrorStack(errorBean.getErrorStack());
                    record.setErrorCode(errorBean.getErrorCode());
                    record.setErrorMessage(errorBean.getDescription());
                }
            }
            branchDao().updateEntityDirectly(record);
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
        this.recordDao().deleteByQuery(query);

        QueryBean subQuery = new QueryBean();
        subQuery.addFilter(FilterBeans.lt(NopTccBranchRecord.PROP_NAME_beginTime, minTime));
        if (onlyCompleted) {
            subQuery.addFilter(FilterBeans.in(NopTccBranchRecord.PROP_NAME_status, TccStatus.getFinishedStatus()));
        }
        this.branchDao().deleteByQuery(subQuery);
    }
}
