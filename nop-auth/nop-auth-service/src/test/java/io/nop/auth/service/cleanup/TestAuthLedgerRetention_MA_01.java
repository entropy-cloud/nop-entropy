package io.nop.auth.service.cleanup;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.dao.entity.NopAuthEmailCode;
import io.nop.auth.dao.entity.NopAuthLoginAttempt;
import io.nop.auth.dao.entity.NopAuthMfaChallenge;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;
import io.nop.auth.dao.entity.NopAuthRateLimitCounter;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.auth.dao.entity.NopAuthSmsCode;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 5.1-MA-01（plan 2502）回归钉：认证六族账本表保留期批量清扫（C10-001/004/005 同 diff）。
 * <p>
 * 断言面（ledger-retention-policy §3.4 成员 3 行）：
 * <ul>
 * <li>C10-001：session 过期行清理 + 行内令牌载荷（accessToken/refreshToken）随行清除 +
 * 未过期行保留（可注入时钟 = retentionTimeMillis 参数 + 行锚列回拨）</li>
 * <li>C10-004：五族（mfa_challenge/sms_code/email_code/rate_limit_counter/login_attempt）
 * 批量清扫参数化断言——仅过期行删除，五族逐一</li>
 * <li>C10-005：{@code countUnexpired} 口径不变钉（仅计未过期行；trusted-device 产品面本批
 * 零改动，清扫后过期行保留——第七面摘除的产品级在测证据）</li>
 * <li>接线验证（rule 23）：doStart 生产生命周期链真实调度（防 C7-005 空壳）+ 被调度命令在
 * 真实 DB 链上删除过期行 + doStop 取消</li>
 * <li>显式禁用（retention-days=0）与 fail-fast 包络（负保留期/非正批删上限/周期低于 60s）</li>
 * </ul>
 * <p>
 * 红证据（E2 变异探针）：基线无批量清扫入口（六族仅同键惰性删除/session 族零惰性——
 * 回退论证断言全红）；实现面变异（purge 体置空）时清理断言全红，快照
 * _tmp/phase4-ma01-red.log / _tmp/phase4-ma01-green.log。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestAuthLedgerRetention_MA_01 extends JunitAutoTestCase {

    private static final long DAY_MS = 24L * 3600_000L;
    private static final String USER_ID = "u-ma01";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    /**
     * C10-001：session 过期行清理 + 令牌载荷随行清除 + 未过期行保留（可注入时钟）。
     * logoutTime 判别通道（已终结行）一并覆盖。
     */
    @Test
    void testMA_01_expiredSessionPurgedWithTokenPayloadUnexpiredRetained() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // 过期行 A：活动时间回拨 31 天（主锚 lastAccessTime 通道），含令牌载荷
        NopAuthSession expired = newSession("sess-ma01-expired");
        expired.setLastAccessTime(new Timestamp(now.getTime() - 31 * DAY_MS).toLocalDateTime());
        expired.setAccessToken("at-expired");
        expired.setRefreshToken("rt-expired");
        sessionDao().saveEntity(expired);

        // 过期行 B：已终结行（logoutTime 回拨 31 天，lastAccessTime 较新——logoutTime 判别通道）
        NopAuthSession terminated = newSession("sess-ma01-terminated");
        terminated.setLastAccessTime(new Timestamp(now.getTime() - 2 * DAY_MS).toLocalDateTime());
        terminated.setLogoutType(AuthApiConstants.LOGOUT_TYPE_EXPIRE);
        terminated.setLogoutTime(new Timestamp(now.getTime() - 31 * DAY_MS));
        sessionDao().saveEntity(terminated);

        // 未到期行：lastAccessTime 为当前，必须保留（令牌载荷不受清扫影响）
        NopAuthSession fresh = newSession("sess-ma01-fresh");
        fresh.setLastAccessTime(new Timestamp(now.getTime()).toLocalDateTime());
        fresh.setAccessToken("at-fresh");
        fresh.setRefreshToken("rt-fresh");
        sessionDao().saveEntity(fresh);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopAuthLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 2, "two expired session rows must be purged, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(sessionDao().getEntityById("sess-ma01-expired"),
                "expired session row must be deleted — token payload (accessToken/refreshToken) dies with the row");
        assertNull(sessionDao().getEntityById("sess-ma01-terminated"),
                "terminated session row (logoutTime anchor) must be deleted");

        NopAuthSession kept = sessionDao().getEntityById("sess-ma01-fresh");
        assertNotNull(kept, "unexpired session row must be retained");
        assertEquals("at-fresh", kept.getAccessToken(), "unexpired session token payload must be intact");
        assertEquals("rt-fresh", kept.getRefreshToken(), "unexpired session refresh token must be intact");
    }

    /**
     * C10-004：五族批量清扫参数化断言（仅过期行删除，五族逐一；expireAt 锚列，跨键清扫——
     * 不再依赖键复访触发惰性删除）。
     */
    @Test
    void testMA_01_fiveFamiliesExpiredRowsSweptUnexpiredRetained() {
        long now = System.currentTimeMillis();
        List<FamilyCase> cases = List.of(
                new FamilyCase("mfa_challenge", this::seedMfaChallenge, this::mfaChallengeExists),
                new FamilyCase("sms_code", this::seedSmsCode, this::smsCodeExists),
                new FamilyCase("email_code", this::seedEmailCode, this::emailCodeExists),
                new FamilyCase("rate_limit_counter", this::seedRateLimitCounter, this::rateLimitCounterExists),
                new FamilyCase("login_attempt", this::seedLoginAttempt, this::loginAttemptExists));

        for (FamilyCase familyCase : cases) {
            familyCase.seed.accept(familyCase.pk("expired"), now - 31 * DAY_MS);
            familyCase.seed.accept(familyCase.pk("fresh"), now + 3600_000L);
        }

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopAuthLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 5, "one expired row per family must be purged, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        for (FamilyCase familyCase : cases) {
            assertFalse(familyCase.exists.test(familyCase.pk("expired")),
                    familyCase.family + ": expired row must be swept across keys (no key revisit required)");
            assertTrue(familyCase.exists.test(familyCase.pk("fresh")),
                    familyCase.family + ": unexpired row must be retained");
        }
    }

    /**
     * A5（plan 2573，2026-11-14 独立评审）：session 族删除腿携带过期谓词——pick（limit 拾取）
     * 与 delete 之间窗口内被访问刷新（lastAccessTime → now）的会话不被删除。
     *
     * <p>缺陷形态（修复前红）：delete 仅按 {@code sessionId IN (...)},不带谓词复验——pick 时点
     * 过期、delete 时点已被复活的会话仍被删（30 天不活跃会话恰在清理瞬间被访问 → 强制终结）。
     * 确定性驱动：直驱包内删除腿 {@code deleteSessionRows}（模拟 pick 结果），其中一行在
     * 「pick 之后」被刷新为 fresh——修复前该行被删（红），修复后谓词复验存活（绿）。
     */
    @Test
    void testMA_01_sessionDeleteRevalidatesPredicateAfterPick_A5() {
        long now = System.currentTimeMillis();
        long cutoff = now - 30 * DAY_MS;

        // pick 时点过期的行：删除腿必须删除
        NopAuthSession expired = newSession("sess-a5-expired");
        expired.setLastAccessTime(new Timestamp(now - 31 * DAY_MS).toLocalDateTime());
        sessionDao().saveEntity(expired);

        // pick 时点过期、delete 时点已被访问刷新（模拟 TOCTOU 窗口内复活）的行：必须存活
        NopAuthSession refreshed = newSession("sess-a5-refreshed");
        refreshed.setLastAccessTime(new Timestamp(now).toLocalDateTime());
        sessionDao().saveEntity(refreshed);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopAuthLedgerCleaner cleaner = newCleaner();
        // 直驱删除腿：模拟「pick 结果 = 两行」之后、delete 之前 refreshed 行被访问刷新
        long deleted = cleaner.deleteSessionRows(List.of("sess-a5-expired", "sess-a5-refreshed"), cutoff);
        assertEquals(1, deleted, "only the still-expired row may be deleted (predicate revalidation)");

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(sessionDao().getEntityById("sess-a5-expired"),
                "row expired at both pick and delete time must be deleted");
        NopAuthSession survivor = sessionDao().getEntityById("sess-a5-refreshed");
        assertNotNull(survivor,
                "A5: a session refreshed between pick and delete must survive (no forced termination)");
        assertEquals(new Timestamp(now).toLocalDateTime(), survivor.getLastAccessTime(),
                "the survivor must be the refreshed row with its fresh lastAccessTime intact");
    }

    /**
     * C10-005：countUnexpired 口径不变钉（仅计未过期行）。trusted-device 产品面本批零改动；
     * 清扫后过期 trusted-device 行保留——第七面（trusted-device 过期行清扫）摘除的
     * 产品级在测证据（清理入口对 NopAuthMfaTrustedDevice 零触碰）。
     */
    /**
     * 第七面并入后契约改写（plan 2569 W7，原 C10-005 六族零触碰钉按新授权范围改形——非弱化：
     * 过期行由第七族通道清扫，未过期行保留钉保留）：trusted-device 过期行被清扫、未过期行保留。
     */
    @Test
    void testMA_01_seventhFamilyExpiredTrustedDeviceSweptUnexpiredRetained() {
        long now = System.currentTimeMillis();
        seedTrustedDevice("td-ma01-fresh-1", now + 10 * DAY_MS);
        seedTrustedDevice("td-ma01-fresh-2", now + 10 * DAY_MS);
        seedTrustedDevice("td-ma01-expired", now - 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        MfaTrustedDeviceManager manager = newManager();
        assertEquals(2L, manager.countUnexpired(USER_ID),
                "C10-005 pin: countUnexpired must count only unexpired rows");

        NopAuthLedgerCleaner cleaner = newCleaner();
        cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertEquals(2L, manager.countUnexpired(USER_ID),
                "unexpired trusted-device rows must be retained by the seventh-family sweeper");
        assertNull(trustedDeviceDao().getEntityById("td-ma01-expired"),
                "expired trusted-device row must be swept by the seventh-family channel (plan 2569 W7)");
    }

    /**
     * 清理幂等：重复触发无害，第二轮零删除。
     */
    @Test
    void testMA_01_purgeIdempotent() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        NopAuthSession expired = newSession("sess-ma01-idem");
        expired.setLastAccessTime(new Timestamp(now.getTime() - 31 * DAY_MS).toLocalDateTime());
        sessionDao().saveEntity(expired);
        seedSmsCode("ma01-idem-code", now.getTime() - 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopAuthLedgerCleaner cleaner = newCleaner();
        long first = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(first >= 2, "first round must delete expired session + sms rows");
        long second = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertEquals(0L, second, "second round must be a no-op (idempotent predicate)");
    }

    /**
     * 接线验证（rule 23）：doStart 生产生命周期链真实调度清理链（taskName/interval 断言），
     * 被调度命令运行时清理入口在真实 DB 链上生效（过期 session 行经 scheduled command 删除——
     * 非仅 API/bean 存在）；doStop 取消。
     */
    @Test
    void testMA_01_lifecycleChainInvokesLedgerCleanup() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        NopAuthSession expired = newSession("sess-ma01-wiring");
        expired.setLastAccessTime(new Timestamp(now.getTime() - 31 * DAY_MS).toLocalDateTime());
        sessionDao().saveEntity(expired);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        CapturingTimer timer = new CapturingTimer();
        NopAuthLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(30);
        cleaner.setLedgerCleanupInterval(java.time.Duration.ofHours(1));
        cleaner.setLedgerDeleteBatchSize(500);

        cleaner.start();

        assertEquals(NopAuthLedgerCleaner.TASK_NAME, timer.taskName.get(),
                "wiring: cleaner lifecycle chain must schedule the ledger cleanup chain");
        assertEquals(3600_000L, timer.delayMillis.get(), "cleanup interval passed to scheduler");

        // 触发被调度的清理命令本体：清理入口在真实 DB 链上生效
        timer.command.get().run();

        ormTemplate.flushSession();
        ormTemplate.clearSession();
        assertNull(sessionDao().getEntityById("sess-ma01-wiring"),
                "expired session must be purged through the scheduled cleanup command (wiring chain)");

        cleaner.stop();
        assertTrue(timer.cancelled.get(), "doStop must cancel the cleanup chain");
    }

    /**
     * retention-days=0 为显式禁用：不调度清理链。
     */
    @Test
    void testMA_01_zeroRetentionDisablesCleanupScheduling() {
        CapturingTimer timer = new CapturingTimer();
        NopAuthLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(0);

        cleaner.start();

        assertNull(timer.taskName.get(), "retention-days=0 must disable cleanup scheduling");
        cleaner.stop();
    }

    /**
     * fail-fast 包络（设计 §3.1 第 5 项）：错配置启动期拒绝，不降级继续。
     */
    @Test
    void testMA_01_invalidLedgerConfigFailsFast() {
        NopAuthLedgerCleaner negativeRetention = newCleaner();
        negativeRetention.setTimer(new CapturingTimer());
        negativeRetention.setWorker(Runnable::run);
        negativeRetention.setLedgerRetentionDays(-1);
        NopException ex1 = assertThrows(NopException.class, negativeRetention::start,
                "negative retention must fail fast at startup");
        assertEquals("nop.err.auth.ledger-config-invalid", ex1.getErrorCode());

        NopAuthLedgerCleaner zeroBatch = newCleaner();
        zeroBatch.setTimer(new CapturingTimer());
        zeroBatch.setWorker(Runnable::run);
        zeroBatch.setLedgerDeleteBatchSize(0);
        NopException ex2 = assertThrows(NopException.class, zeroBatch::start,
                "non-positive batch cap must fail fast at startup");
        assertEquals("nop.err.auth.ledger-config-invalid", ex2.getErrorCode());

        NopAuthLedgerCleaner tinyInterval = newCleaner();
        tinyInterval.setTimer(new CapturingTimer());
        tinyInterval.setWorker(Runnable::run);
        tinyInterval.setLedgerCleanupInterval(java.time.Duration.ofSeconds(30));
        NopException ex3 = assertThrows(NopException.class, tinyInterval::start,
                "cleanup interval below 60s floor must fail fast at startup");
        assertEquals("nop.err.auth.ledger-config-invalid", ex3.getErrorCode());
    }

    // ==================== 测试载体 ====================

    /** 五族参数化用例（seed + 存在性判定）。 */
    record FamilyCase(String family, BiConsumer<String, Long> seed, Predicate<String> exists) {
        String pk(String suffix) {
            return "ma01-" + family + "-" + suffix;
        }
    }

    /** 捕获调度参数的 timer（wiring 断言载体）。 */
    static class CapturingTimer implements IScheduledExecutor {
        final AtomicReference<String> taskName = new AtomicReference<>();
        final AtomicReference<Long> delayMillis = new AtomicReference<>();
        final AtomicReference<Runnable> command = new AtomicReference<>();
        final AtomicReference<Boolean> cancelled = new AtomicReference<>(false);

        class ObservedFuture extends CompletableFuture<Object> {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit,
                                                String taskName, IPeriodicTaskListener listener) {
            this.taskName.set(taskName);
            this.delayMillis.set(unit.toMillis(delay));
            this.command.set(command);
            return new ObservedFuture();
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            this.command.set(command);
            this.delayMillis.set(unit.toMillis(delay));
            return new ObservedFuture();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        /**
         * 生产形态为 BindScheduledExecutor 包装（timer 只触发、任务体 hop worker）；本捕获载体
         * 原样返回 this，使六参绝缘入口的调度参数（taskName/interval/command）可被直接观测。
         */
        @Override
        public IScheduledExecutor executeOn(java.util.concurrent.Executor executor) {
            return this;
        }

        @Override
        public <V> CompletableFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(java.util.concurrent.Callable<V> callable) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return new CompletableFuture<>();
        }

        @Override
        public void destroy() {
            // 测试载体：无可释放资源
        }

        @Override
        public void refreshConfig() {
            // 测试载体：无热更面
        }

        @Override
        public String getName() {
            return "capturing-timer";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }
    }

    // ==================== Helpers ====================

    private NopAuthLedgerCleaner newCleaner() {
        NopAuthLedgerCleaner cleaner = new NopAuthLedgerCleaner();
        cleaner.setDaoProvider(daoProvider);
        return cleaner;
    }

    /** trusted-device manager 只读载体（C10-005 钉）：反射注入 daoProvider，零产品面改动。 */
    private MfaTrustedDeviceManager newManager() {
        MfaTrustedDeviceManager manager = new MfaTrustedDeviceManager();
        try {
            java.lang.reflect.Field f = MfaTrustedDeviceManager.class.getDeclaredField("daoProvider");
            f.setAccessible(true);
            f.set(manager, daoProvider);
            // 5.1-MA-04（plan 2569）：可选依赖注入缺位容忍（isExempted/register 旧测试面不触锁/orm 通道时无需）
            try {
                java.lang.reflect.Field lf = MfaTrustedDeviceManager.class.getDeclaredField("lockManager");
                lf.setAccessible(true);
                lf.set(manager, new io.nop.commons.concurrent.lock.impl.LocalResourceLockManager());
                java.lang.reflect.Field of = MfaTrustedDeviceManager.class.getDeclaredField("ormTemplate");
                of.setAccessible(true);
                of.set(manager, ormTemplate);
            } catch (NoSuchFieldException ignored) {
                // 修复前代码形态容忍
            }
        } catch (ReflectiveOperationException e) {
            throw NopException.adapt(e);
        }
        return manager;
    }

    private NopAuthSession newSession(String sessionId) {
        NopAuthSession session = sessionDao().newEntity();
        session.setSessionId(sessionId);
        session.setUserId(USER_ID);
        session.setUserName("user-ma01");
        session.setTenantId("0");
        session.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        session.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE);
        session.setLoginTime(new Timestamp(System.currentTimeMillis()));
        return session;
    }

    private void seedMfaChallenge(String token, Long expireAt) {
        NopAuthMfaChallenge e = mfaChallengeDao().newEntity();
        e.setChallengeToken(token);
        e.setUserId(USER_ID);
        e.setMfaType("totp");
        e.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        e.setFailCount(0);
        e.setScene("login");
        e.setExpireAt(expireAt);
        mfaChallengeDao().saveEntityDirectly(e);
    }

    private void seedSmsCode(String key, Long expireAt) {
        NopAuthSmsCode e = smsCodeDao().newEntity();
        e.setCodeKey(key);
        e.setCode("123456");
        e.setFailCount(0);
        e.setExpireAt(expireAt);
        smsCodeDao().saveEntityDirectly(e);
    }

    private void seedEmailCode(String key, Long expireAt) {
        NopAuthEmailCode e = emailCodeDao().newEntity();
        e.setCodeKey(key);
        e.setCode("123456");
        e.setFailCount(0);
        e.setExpireAt(expireAt);
        emailCodeDao().saveEntityDirectly(e);
    }

    private void seedRateLimitCounter(String key, Long expireAt) {
        NopAuthRateLimitCounter e = rateLimitCounterDao().newEntity();
        e.setCounterKey(key);
        e.setCounterCount(1L);
        e.setExpireAt(expireAt);
        rateLimitCounterDao().saveEntityDirectly(e);
    }

    private void seedLoginAttempt(String key, Long expireAt) {
        NopAuthLoginAttempt e = loginAttemptDao().newEntity();
        e.setAttemptKey(key);
        e.setFailCount(1);
        e.setExpireAt(expireAt);
        loginAttemptDao().saveEntityDirectly(e);
    }

    private void seedTrustedDevice(String sid, Long expireAt) {
        NopAuthMfaTrustedDevice e = trustedDeviceDao().newEntity();
        e.setSid(sid);
        e.setUserId(USER_ID);
        e.setDeviceHash(StringHelper.generateUUID(16));
        e.setDeviceName("device-ma01");
        e.setExpireAt(new Timestamp(expireAt));
        trustedDeviceDao().saveEntity(e);
    }

    private boolean mfaChallengeExists(String token) {
        return mfaChallengeDao().getEntityById(token) != null;
    }

    private boolean smsCodeExists(String key) {
        return smsCodeDao().getEntityById(key) != null;
    }

    private boolean emailCodeExists(String key) {
        return emailCodeDao().getEntityById(key) != null;
    }

    private boolean rateLimitCounterExists(String key) {
        return rateLimitCounterDao().getEntityById(key) != null;
    }

    private boolean loginAttemptExists(String key) {
        return loginAttemptDao().getEntityById(key) != null;
    }

    private IEntityDao<NopAuthSession> sessionDao() {
        return daoProvider.daoFor(NopAuthSession.class);
    }

    private IEntityDao<NopAuthMfaChallenge> mfaChallengeDao() {
        return daoProvider.daoFor(NopAuthMfaChallenge.class);
    }

    private IEntityDao<NopAuthSmsCode> smsCodeDao() {
        return daoProvider.daoFor(NopAuthSmsCode.class);
    }

    private IEntityDao<NopAuthEmailCode> emailCodeDao() {
        return daoProvider.daoFor(NopAuthEmailCode.class);
    }

    private IEntityDao<NopAuthRateLimitCounter> rateLimitCounterDao() {
        return daoProvider.daoFor(NopAuthRateLimitCounter.class);
    }

    private IEntityDao<NopAuthLoginAttempt> loginAttemptDao() {
        return daoProvider.daoFor(NopAuthLoginAttempt.class);
    }

    private IEntityDao<NopAuthMfaTrustedDevice> trustedDeviceDao() {
        return daoProvider.daoFor(NopAuthMfaTrustedDevice.class);
    }
}
