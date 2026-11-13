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
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.tcc.api.*;
import io.nop.tcc.core.TccEngineConfigs;
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

import static io.nop.tcc.api.TccErrors.ARG_BRANCH_ID;
import static io.nop.tcc.api.TccErrors.ARG_FROM_STATUS;
import static io.nop.tcc.api.TccErrors.ARG_TO_STATUS;
import static io.nop.tcc.api.TccErrors.ARG_TXN_ID;
import static io.nop.tcc.api.TccErrors.ERR_TCC_STATUS_CAS_CONFLICT;

public class TccRecordStore implements ITccRecordStore {
    static final Logger LOG = LoggerFactory.getLogger(TccRecordStore.class);

    private IDaoProvider daoProvider;

    // 可空：单测直连构造（绕过 IoC）时无事务模板，CAS 方法退化为无事务立即结算
    private ITransactionTemplate txnTemplate;

    private int defaultBranchTimeout;

    private int defaultTxnTimeout;

    private int defaultMaxRetryTimes;

    // 1.6-C8-003（plan 2543 W-13）：两参清理形态的内部分页批大小（null = 回退配置键）
    private Integer cleanupBatchSize;

    /** 1.6-C8-003（plan 2543 W-13）：测试/装配侧批大小覆盖。 */
    public void setCleanupBatchSize(Integer cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

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

    @Inject
    public void setTransactionTemplate(ITransactionTemplate txnTemplate) {
        this.txnTemplate = txnTemplate;
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
        Timestamp beginTime = nowTimestampForWrite(dao);
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
        branchRecord.setBeginTime(nowTimestampForWrite(branchDao()));
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

    @Override
    public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
        return FutureHelper.futureCall(() -> runInNewTxn(() -> doUpdateTccStatus(record, status, error)));
    }

    /**
     * plan 2515 裁定②（提交可见性）：CAS future 必须在事务提交之后完成——自管 REQUIRES_NEW
     * 事务（TransactionTemplate.runInTransactionAsync），commit 在内层 stage 完成后执行，
     * 调用方可见的 future 在 afterCommit 完成成、rollback/commit-fail 分支以异常完成（配对语义，
     * 零「永不完成」残留）；无事务模板可用（单测直连构造）时立即结算。方法体同步异常经
     * FutureHelper.futureCall 即时 failedStage。
     */
    private CompletionStage<Void> runInNewTxn(java.util.concurrent.Callable<CompletionStage<Void>> task) {
        if (txnTemplate == null) {
            try {
                return task.call();
            } catch (Throwable t) {
                return FutureHelper.reject(t);
            }
        }
        return txnTemplate.runInTransactionAsync(null, TransactionPropagation.REQUIRES_NEW,
                        txn -> {
                            try {
                                return task.call();
                            } catch (Throwable t) {
                                return FutureHelper.reject(t);
                            }
                        });
    }

    private CompletionStage<Void> doUpdateTccStatus(ITccRecord record, TccStatus status, Throwable error) {
        return FutureHelper.futureCall(() -> {
            NopTccRecord tccRecord = (NopTccRecord) record;
            // from状态必须在修改内存status之前捕获，updateByQuery按[id+from状态]做单语句CAS：
            // 并发writer/stale writer导致DB状态已变化时本次更新0行
            Integer fromStatus = tccRecord.getStatus();

            Map<String, Object> props = new HashMap<>();
            props.put(NopTccRecord.PROP_NAME_status, status.getCode());

            if (status.isFinished()) {
                Timestamp endTime = nowTimestampForWrite(recordDao());
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
                // plan 2515 裁定①③：CAS 0 行=并发冲突（互斥守卫的陈旧内存副本已失效），
                // WARN 留痕后显式异常完成——吞为正常返回会使补偿 RPC 对撞照发/误报终态直写
                LOG.warn("nop.tcc.skip-status-update-due-to-conflict:txnId={},fromStatus={},toStatus={}",
                        tccRecord.getTxnId(), fromStatus, status.getCode());
                throw new NopException(ERR_TCC_STATUS_CAS_CONFLICT)
                        .param(ARG_TXN_ID, tccRecord.getTxnId())
                        .param(ARG_BRANCH_ID, "")
                        .param(ARG_FROM_STATUS, fromStatus)
                        .param(ARG_TO_STATUS, status.getCode());
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
            record.setBeginTime(nowTimestampForWrite(branchDao()));
            if (record.getExpireTime() == null)
                record.setExpireTime(new Timestamp(record.getBeginTime().getTime() + defaultBranchTimeout));
            branchDao().saveEntityDirectly(record);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> updateTccBranchStatusAsync(ITccBranchRecord branchRecord, TccStatus status, Throwable error) {
        return FutureHelper.futureCall(() -> runInNewTxn(() -> doUpdateTccBranchStatus(branchRecord, status, error)));
    }

    private CompletionStage<Void> doUpdateTccBranchStatus(ITccBranchRecord branchRecord, TccStatus status, Throwable error) {
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
                Timestamp endTime = nowTimestampForWrite(branchDao());
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
                // plan 2515 裁定①②：分支 CAS 0 行=并发冲突，WARN 留痕后显式异常完成——
                // 吞为正常返回且不回写内存，会使聚合按陈旧副本折叠误报终态（预留资源永久悬挂）
                LOG.warn("nop.tcc.skip-branch-status-update-due-to-conflict:txnId={},branchId={},fromStatus={},toStatus={}",
                        record.getTxnId(), record.getBranchId(), fromStatus, status.getCode());
                throw new NopException(ERR_TCC_STATUS_CAS_CONFLICT)
                        .param(ARG_TXN_ID, record.getTxnId())
                        .param(ARG_BRANCH_ID, record.getBranchId())
                        .param(ARG_FROM_STATUS, fromStatus)
                        .param(ARG_TO_STATUS, status.getCode());
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

        // W-08：判定用下界时钟（只有确定已过期才触发恢复）；推进 expireTime 是写入，用上界时钟
        Timestamp minTime = new Timestamp(nowForRead(dao) - expireGap);
        Timestamp nextCheckTime = new Timestamp(nowForWrite(dao) + checkInterval);

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

    /**
     * 1.6-C7-007（plan 2543 W-08）：读侧时钟 seam——过期判定/清理窗口的**下界**。
     * 用 DB 估算时钟的 min 值：只有「确定已过期」才触发恢复，与 {@code expireTime < minTime}
     * 谓词同向；本地墙钟 NTP 步进不再直接改写过期判定基准。
     */
    protected long nowForRead(IOrmEntityDao<NopTccRecord> dao) {
        return dao.getDbEstimatedClock().getMinCurrentTimeMillis();
    }

    /**
     * 1.6-C7-007（plan 2543 W-08）：写侧时钟 seam——所有落库时间戳（beginTime/expireTime/
     * endTime/检查点推进）的**上界**。写入偏后是保守方向，绝不提前触发恢复。
     */
    protected long nowForWrite(IOrmEntityDao<NopTccRecord> dao) {
        return dao.getDbEstimatedClock().getMaxCurrentTimeMillis();
    }

    protected Timestamp nowTimestampForWrite(IEntityDao<?> dao) {
        return new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis());
    }

    /**
     * 1.6-C8-003（plan 2543 W-13）：两参形态保留公共签名（零 api 变更），实现改为**内部按批分页**——
     * 原 `findAllByQuery` 无 limit 会一次性物化保留期窗口内的全部完结事务实体，再构造等长 txnIds
     * 与巨型 IN 子句（百万行级 → OOM/ORM 会话膨胀/单语句压力，清理自身失败后账本继续增长）。
     * <p>
     * 事务形态显式裁定（设计 §3.3.4）：整个调用仍在**单一事务**内（不逐批独立事务）——两参是公共
     * 契约，调用方期望「一次调用全删或全不删」的原子性；循环体是私有非注解助手，故不触发
     * 2515 记录的「自调用绕过事务拦截器」陷阱。生产清理走三参形态（TccEngine.runLedgerCleanup），
     * 长事务面不在生产路径上。
     */
    @Override
    @Transactional
    public void removeCompletedRecords(long retentionTime, boolean onlyCompleted) {
        int batchSize = resolveCleanupBatchSize();
        int totalDeleted = 0;
        while (true) {
            int deleted = doRemoveCompletedBatch(retentionTime, onlyCompleted, batchSize);
            if (deleted <= 0)
                break;
            totalDeleted += deleted;
            LOG.info("nop.tcc.ledger.remove-completed-progress:batchSize={},deleted={},total={}",
                    batchSize, deleted, totalDeleted);
        }
    }

    /**
     * 1.6-C8-003（plan 2543 W-13）：批助手（私有、非注解）——按锚列 beginTime 有界拾取后
     * 分支随父级联删除，返回本批删除的主记录行数。
     */
    private int doRemoveCompletedBatch(long retentionTime, boolean onlyCompleted, int batchSize) {
        QueryBean query = new QueryBean();
        Timestamp minTime = new Timestamp(nowForRead(recordDao()) - retentionTime);
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_beginTime, minTime));
        if (onlyCompleted)
            query.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_status, TccStatus.getFinishedStatus()));
        query.setLimit(batchSize);

        // 分支记录随父记录生命周期清理：按已完结父记录的txnId集合删除分支。
        // 此前分支按自身status+beginTime独立删除，父已删而分支非终态时（如BIZ_CANCEL_FAILED
        // 全局配CANCEL_FAILED分支）产生永不清理的孤儿分支
        List<NopTccRecord> records = recordDao().findAllByQuery(query);
        if (records.isEmpty())
            return 0;

        List<String> txnIds = new ArrayList<>(records.size());
        for (NopTccRecord record : records) {
            txnIds.add(record.getTxnId());
        }
        QueryBean branchQuery = new QueryBean();
        branchQuery.addFilter(FilterBeans.in(NopTccBranchRecord.PROP_NAME_txnId, txnIds));
        branchDao().deleteByQuery(branchQuery);

        QueryBean deleteQuery = new QueryBean();
        deleteQuery.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_txnId, txnIds));
        return (int) recordDao().deleteByQuery(deleteQuery);
    }

    private int resolveCleanupBatchSize() {
        Integer configured = cleanupBatchSize;
        return configured != null && configured > 0 ? configured
                : TccEngineConfigs.CFG_TCC_LEDGER_DELETE_BATCH_SIZE.get();
    }

    /**
     * plan 2515 裁定⑧：耗尽行前置终态化。retryTimes>=maxRetryCount 且已过期且未完结的记录
     * 被恢复扫描与 finished 清理谓词双重永久排除——先终态化为 KILLED（endTime 落账）再入清理谓词，
     * 保留审计窗。有界批处理（拾取 limit batchSize）防大表首扫冲击。
     */
    @Override
    @Transactional
    public int killExhaustedRecords(int maxRetryCount, int batchSize) {
        IOrmEntityDao<NopTccRecord> dao = recordDao();

        Timestamp now = nowTimestampForWrite(dao);
        QueryBean query = new QueryBean();
        // 已过期
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_expireTime, now));
        // 未完结（终态之前的所有状态）
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
        // 重试次数已达上限（与 fetchExpiredRecords 的 retryTimes<maxRetryCount 拾取条件互补）
        query.addFilter(FilterBeans.ge(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
        query.setLimit(batchSize);

        List<NopTccRecord> records = dao.findAllByQuery(query);
        if (records.isEmpty())
            return 0;

        List<String> txnIds = new ArrayList<>(records.size());
        for (NopTccRecord record : records) {
            txnIds.add(record.getTxnId());
        }

        Map<String, Object> props = new HashMap<>();
        props.put(NopTccRecord.PROP_NAME_status, TccStatus.KILLED.getCode());
        props.put(NopTccRecord.PROP_NAME_endTime, now);

        QueryBean updateQuery = new QueryBean();
        updateQuery.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_txnId, txnIds));
        updateQuery.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
        long updated = dao.updateByQuery(updateQuery, props);
        LOG.info("nop.tcc.ledger.kill-exhausted-rows:maxRetryCount={},batchSize={},killed={}",
                maxRetryCount, batchSize, updated);
        return (int) updated;
    }

    /**
     * plan 2515 裁定⑧：removeCompletedRecords 的有界批删形态——按锚列 beginTime 拾取
     * （limit batchSize）+ 分支随父级联，返回删除的主记录行数。
     */
    @Override
    @Transactional
    public int removeCompletedRecords(long retentionTime, boolean onlyCompleted, int batchSize) {
        QueryBean query = new QueryBean();
        Timestamp minTime = new Timestamp(nowForRead(recordDao()) - retentionTime);
        query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_beginTime, minTime));
        if (onlyCompleted)
            query.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_status, TccStatus.getFinishedStatus()));
        query.setLimit(batchSize);

        List<NopTccRecord> records = recordDao().findAllByQuery(query);
        if (records.isEmpty())
            return 0;

        List<String> txnIds = new ArrayList<>(records.size());
        for (NopTccRecord record : records) {
            txnIds.add(record.getTxnId());
        }
        QueryBean branchQuery = new QueryBean();
        branchQuery.addFilter(FilterBeans.in(NopTccBranchRecord.PROP_NAME_txnId, txnIds));
        branchDao().deleteByQuery(branchQuery);

        QueryBean deleteQuery = new QueryBean();
        deleteQuery.addFilter(FilterBeans.in(NopTccRecord.PROP_NAME_txnId, txnIds));
        long deleted = recordDao().deleteByQuery(deleteQuery);
        LOG.info("nop.tcc.ledger.remove-completed:retentionTime={},onlyCompleted={},batchSize={},deleted={}",
                retentionTime, onlyCompleted, batchSize, deleted);
        return (int) deleted;
    }
}
