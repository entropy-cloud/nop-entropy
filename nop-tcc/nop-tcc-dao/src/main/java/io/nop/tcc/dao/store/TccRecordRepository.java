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
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccRecordRepository;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.dao.entity.NopTccRecord;

import jakarta.inject.Inject;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class TccRecordRepository implements ITccRecordRepository {

    private IDaoProvider daoProvider;

    private int defaultBranchTimeout;

    @InjectValue("@cfg:nop.tcc.default-branch-timeout-ms|10000")
    public void setDefaultBranchTimeout(int defaultBranchTimeoutMs) {
        this.defaultBranchTimeout = defaultBranchTimeoutMs;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    private IEntityDao<NopTccRecord> recordDao() {
        return daoProvider.daoFor(NopTccRecord.class);
    }

    private IEntityDao<NopTccBranchRecord> branchDao() {
        return daoProvider.daoFor(NopTccBranchRecord.class);
    }

    @Override
    public ITccRecord newTccRecord(String txnGroup) {
        IEntityDao<NopTccRecord> dao = recordDao();

        NopTccRecord record = new NopTccRecord();
        record.setTxnGroup(txnGroup);
        record.setBeginTime(CoreMetrics.currentTimestamp());
        record.setStatus(TccStatus.CREATED.getCode());
        dao.initEntityId(record);
        return record;
    }

    @Override
    public ITccBranchRecord newBranchRecord(ITccRecord record, TccBranchRequest request) {
        IEntityDao<NopTccBranchRecord> dao = branchDao();

        NopTccBranchRecord branchRecord = new NopTccBranchRecord();
        branchRecord.setTxnId(record.getTxnId());
        branchRecord.setBeginTime(CoreMetrics.currentTimestamp());
        branchRecord.setRequestData(JsonTool.stringify(request.getRequest()));
        branchRecord.setCancelMethod(request.getCancelMethod());
        branchRecord.setConfirmMethod(request.getConfirmMethod());
        branchRecord.setServiceMethod(request.getServiceMethod());
        branchRecord.setServiceName(request.getServiceName());
        branchRecord.setStatus(TccStatus.CREATED.getCode());
        dao.initEntityId(branchRecord);

        return branchRecord;
    }

    @Override
    public CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId) {
        return FutureHelper.futureCall(() -> {
            return recordDao().getEntityById(txnId);
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
            return tccRecord;
        });
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
        return FutureHelper.futureCall(() -> {
            NopTccRecord tccRecord = (NopTccRecord) record;
            tccRecord.setStatus(status.getCode());

            if (error != null) {
                ErrorBean errorBean = getErrorBean(error);
                tccRecord.setErrorStack(errorBean.getErrorStack());
                tccRecord.setErrorCode(errorBean.getErrorCode());
                tccRecord.setErrorMessage(errorBean.getDescription());
            }
            recordDao().updateEntityDirectly(tccRecord);
            return tccRecord;
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
            return record;
        });
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public CompletionStage<Void> updateTccBranchStatusAsync(ITccBranchRecord branchRecord, TccStatus status, Throwable error) {
        return FutureHelper.futureCall(() -> {
            NopTccBranchRecord record = (NopTccBranchRecord) branchRecord;
            record.setStatus(status.getCode());

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
            return record;
        });
    }

    protected ErrorBean getErrorBean(Throwable error) {
        return ErrorMessageManager.instance().buildErrorMessage(null, error);
    }

    @Override
    public void forEachExpiredRecord(Function<ITccRecord, CompletionStage<Void>> consumer, long expireGap, int maxRetryCount, ICancelToken cancelToken) {

    }

    @Override
    public void removeCompletedRecords(long retentionTime) {

    }
}
