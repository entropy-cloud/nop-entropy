package io.nop.auth.service.cleanup;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.dao.entity.NopAuthEmailCode;
import io.nop.auth.dao.entity.NopAuthLoginAttempt;
import io.nop.auth.dao.entity.NopAuthMfaChallenge;
import io.nop.auth.dao.entity.NopAuthRateLimitCounter;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.auth.dao.entity.NopAuthSmsCode;
import io.nop.auth.dao.entity._gen._NopAuthEmailCode;
import io.nop.auth.dao.entity._gen._NopAuthLoginAttempt;
import io.nop.auth.dao.entity._gen._NopAuthMfaChallenge;
import io.nop.auth.dao.entity._gen._NopAuthRateLimitCounter;
import io.nop.auth.dao.entity._gen._NopAuthSession;
import io.nop.auth.dao.entity._gen._NopAuthSmsCode;
import io.nop.auth.service.NopAuthConfigs;
import io.nop.auth.service.NopAuthErrors;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.nop.auth.service.NopAuthErrors.ARG_CONFIG_KEY;
import static io.nop.auth.service.NopAuthErrors.ARG_CONFIG_VALUE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_LEDGER_CONFIG_INVALID;

/**
 * 5.1-MA-01（plan 2502）：认证六族账本表保留期批量清扫（形态 A 周期低频接入）。
 * <p>
 * 六族 = NopAuthSession（含 accessToken/refreshToken/cacheData 令牌载荷行）+ 五族短 TTL 临时行
 * （mfa_challenge / sms_code / email_code / rate_limit_counter / login_attempt）。五族既有语义为
 * 「同键访问时惰性删除」——键不复访即永久驻留（RateLimitCounter/LoginAttempt 键空间可被攻击者
 * 低成本扩充）；session 族连惰性删除都没有。本组件提供跨键批量清扫的清理配对（credential
 * {@code NopCredentialOauthStateStore.cleanupExpired()} 语句形态），六族一处实现、统一接入、
 * 处置一致不静默分叉。
 * <p>
 * 参数语义 SSOT = ai-dev/design/concurrency/ledger-retention-policy.md §3.4 成员 3 行 +
 * §3.2 nop-auth 行（键名/缺省值照抄，模块不发明）。清理谓词时间锚列显式落名：
 * <ul>
 * <li>{@code nop_auth_session}：锚列 lastAccessTime（主锚）+ logoutTime 判别（已终结行）——
 * 行删除即令牌载荷清除（accessToken/refreshToken/cacheData 随行消亡）</li>
 * <li>五族：锚列 {@code expireAt}，谓词 {@code expireAt < now - retention}
 * （过期后滞留超过保留窗才清扫——宁长勿短，与 session 族同参同窗）</li>
 * </ul>
 * 生命周期接线（防 C7-005「清理 API 存在但零生产装配」空壳形态）：IoC 容器创建单例 bean →
 * {@link LifeCycleSupport} {@code @PostConstruct start()} → doStart 周期调度（绝缘六参入口，
 * timer 只触发、任务体 hop worker，taskName=nop-auth-ledger-cleanup）。错配置（负保留期/
 * 非正批删上限/周期低于 60s 下限）启动期 fail-fast；retention-days=0 显式禁用。清理体任何
 * Throwable 由绝缘层（IPeriodicTaskListener）观测并重臂，失败 ERROR 显式可见 + 失败计数——
 * 不静默吞掉。
 * <p>
 * 授权边界（ask-first 2026-10-25）：本组件仅覆盖六族；trusted-device 过期行清扫第七面
 * （MfaTrustedDeviceManager）已由 plan 2502 R1 修订摘除，归 Deferred/R3-01——本组件不包含
 * NopAuthMfaTrustedDevice 通道。
 */
public class NopAuthLedgerCleaner extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(NopAuthLedgerCleaner.class);

    public static final String TASK_NAME = "nop-auth-ledger-cleanup";

    private IDaoProvider daoProvider;
    private IScheduledExecutor timer;
    private java.util.concurrent.Executor worker;

    // null = 未显式注入，回退 NopAuthConfigs 声明的键值（缺省值唯一事实源在 Configs 接口）
    private Integer ledgerRetentionDays;
    private Duration ledgerCleanupInterval;
    private Integer ledgerDeleteBatchSize;

    private Future<?> cleanupFuture;
    private final AtomicLong cleanupFailureCount = new AtomicLong();

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    public void setWorker(java.util.concurrent.Executor worker) {
        this.worker = worker;
    }

    public void setLedgerRetentionDays(Integer ledgerRetentionDays) {
        this.ledgerRetentionDays = ledgerRetentionDays;
    }

    public void setLedgerCleanupInterval(Duration ledgerCleanupInterval) {
        this.ledgerCleanupInterval = ledgerCleanupInterval;
    }

    public void setLedgerDeleteBatchSize(Integer ledgerDeleteBatchSize) {
        this.ledgerDeleteBatchSize = ledgerDeleteBatchSize;
    }

    public long getCleanupFailureCount() {
        return cleanupFailureCount.get();
    }

    @Override
    protected void doStart() {
        int retentionDays = resolveLedgerRetentionDays();
        long intervalMillis = resolveLedgerCleanupInterval().toMillis();
        int batchSize = resolveLedgerDeleteBatchSize();

        if (retentionDays < 0) {
            throw new NopException(ERR_AUTH_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.auth.ledger.retention-days")
                    .param(ARG_CONFIG_VALUE, retentionDays);
        }
        if (batchSize <= 0) {
            throw new NopException(ERR_AUTH_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.auth.ledger.delete-batch-size")
                    .param(ARG_CONFIG_VALUE, batchSize);
        }
        if (intervalMillis < NopAuthConfigs.AUTH_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS) {
            throw new NopException(ERR_AUTH_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.auth.ledger.cleanup-interval")
                    .param(ARG_CONFIG_VALUE, intervalMillis);
        }
        if (retentionDays == 0) {
            LOG.info("nop.auth.ledger.cleanup-disabled");
            return;
        }

        if (timer == null)
            timer = GlobalExecutors.globalTimer();
        if (worker == null)
            worker = GlobalExecutors.globalWorker();

        long retentionMillis = retentionDays * 24L * 3600_000L;
        cleanupFuture = timer.executeOn(worker).scheduleWithFixedDelay(() -> purgeExpiredLedgerRows(
                        retentionMillis, batchSize),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS,
                TASK_NAME, new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.auth.ledger.cleanup-fail:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.auth.ledger.cleanup-chain-rejected: cleanup chain terminated"
                                + " by executor rejection, will not resurrect", e);
                    }
                });
        LOG.info("nop.auth.ledger.cleanup-started:retentionDays={},intervalMillis={},batchSize={}",
                retentionDays, intervalMillis, batchSize);
    }

    @Override
    protected void doStop() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
            cleanupFuture = null;
            LOG.info("nop.auth.ledger.cleanup-stopped");
        }
    }

    /**
     * 单轮六族清扫（5.1-MA-01）。清理体失败直接抛出（由绝缘 listener 观测），不在本方法内吞异常。
     *
     * @param retentionTimeMillis 保留期（毫秒）
     * @param batchSize           单轮单表删除上限
     * @return 本轮删除行数（六族合计）
     */
    public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
        long now = sessionDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        long cutoffMillis = now - retentionTimeMillis;
        long total = 0L;

        // ① session 族：锚列 lastAccessTime（主锚）+ logoutTime 判别（已终结行）。
        // 行删除即令牌载荷（accessToken/refreshToken/cacheData）清除。
        total += purgeExpiredSessions(cutoffMillis, batchSize);

        // ②~⑥ 五族短 TTL 临时行：锚列 expireAt（epoch 毫秒 BIGINT），谓词 expireAt < cutoff
        // （过期后滞留超保留窗才清扫）。锚列逐表显式传参（设计 §3.1 第 7 项：禁止隐式选列）。
        total += purgeExpiredByExpireAt(NopAuthMfaChallenge.class, _NopAuthMfaChallenge.PROP_NAME_challengeToken,
                NopAuthMfaChallenge::getChallengeToken, _NopAuthMfaChallenge.PROP_NAME_expireAt, cutoffMillis, batchSize);
        total += purgeExpiredByExpireAt(NopAuthSmsCode.class, _NopAuthSmsCode.PROP_NAME_codeKey,
                NopAuthSmsCode::getCodeKey, _NopAuthSmsCode.PROP_NAME_expireAt, cutoffMillis, batchSize);
        total += purgeExpiredByExpireAt(NopAuthEmailCode.class, _NopAuthEmailCode.PROP_NAME_codeKey,
                NopAuthEmailCode::getCodeKey, _NopAuthEmailCode.PROP_NAME_expireAt, cutoffMillis, batchSize);
        total += purgeExpiredByExpireAt(NopAuthRateLimitCounter.class, _NopAuthRateLimitCounter.PROP_NAME_counterKey,
                NopAuthRateLimitCounter::getCounterKey, _NopAuthRateLimitCounter.PROP_NAME_expireAt, cutoffMillis, batchSize);
        total += purgeExpiredByExpireAt(NopAuthLoginAttempt.class, _NopAuthLoginAttempt.PROP_NAME_attemptKey,
                NopAuthLoginAttempt::getAttemptKey, _NopAuthLoginAttempt.PROP_NAME_expireAt, cutoffMillis, batchSize);

        return total;
    }

    private long purgeExpiredSessions(long cutoffMillis, int batchSize) {
        IEntityDao<NopAuthSession> dao = sessionDao();
        QueryBean pick = new QueryBean();
        pick.setLimit(batchSize);
        // 主锚 lastAccessTime（LocalDateTime 列）+ logoutTime 判别（Timestamp 列；null 行谓词为假自然排除）
        pick.addFilter(FilterBeans.or(
                FilterBeans.lt(_NopAuthSession.PROP_NAME_lastAccessTime, new Timestamp(cutoffMillis).toLocalDateTime()),
                FilterBeans.lt(_NopAuthSession.PROP_NAME_logoutTime, new Timestamp(cutoffMillis))));
        List<NopAuthSession> expired = dao.findAllByQuery(pick);
        if (expired.isEmpty())
            return 0L;
        List<String> sessionIds = new ArrayList<>(expired.size());
        for (NopAuthSession session : expired) {
            sessionIds.add(session.getSessionId());
        }
        QueryBean delete = new QueryBean();
        delete.addFilter(FilterBeans.in(_NopAuthSession.PROP_NAME_sessionId, sessionIds));
        return dao.deleteByQuery(delete);
    }

    private <T extends io.nop.dao.api.IDaoEntity> long purgeExpiredByExpireAt(Class<T> entityClass, String pkProp, Function<T, Object> pkGetter,
                                                                              String anchorProp, long cutoffMillis, int batchSize) {
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        QueryBean pick = new QueryBean();
        pick.setLimit(batchSize);
        // expireAt 为 epoch 毫秒 BIGINT 列，谓词值绑定 long 截止时刻
        pick.addFilter(FilterBeans.lt(anchorProp, cutoffMillis));
        List<T> rows = dao.findAllByQuery(pick);
        if (rows.isEmpty())
            return 0L;
        List<Object> pks = new ArrayList<>(rows.size());
        for (T row : rows) {
            pks.add(pkGetter.apply(row));
        }
        QueryBean delete = new QueryBean();
        delete.addFilter(FilterBeans.in(pkProp, pks));
        return dao.deleteByQuery(delete);
    }

    private int resolveLedgerRetentionDays() {
        return ledgerRetentionDays != null ? ledgerRetentionDays
                : NopAuthConfigs.CFG_AUTH_LEDGER_RETENTION_DAYS.get();
    }

    private Duration resolveLedgerCleanupInterval() {
        return ledgerCleanupInterval != null ? ledgerCleanupInterval
                : NopAuthConfigs.CFG_AUTH_LEDGER_CLEANUP_INTERVAL.get();
    }

    private int resolveLedgerDeleteBatchSize() {
        return ledgerDeleteBatchSize != null ? ledgerDeleteBatchSize
                : NopAuthConfigs.CFG_AUTH_LEDGER_DELETE_BATCH_SIZE.get();
    }

    private IEntityDao<NopAuthSession> sessionDao() {
        return daoProvider.daoFor(NopAuthSession.class);
    }
}
