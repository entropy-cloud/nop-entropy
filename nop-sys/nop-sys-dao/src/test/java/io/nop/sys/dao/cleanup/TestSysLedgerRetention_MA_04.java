package io.nop.sys.dao.cleanup;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysChangeLog;
import io.nop.sys.dao.entity.NopSysLock;
import io.nop.sys.dao.log.OrmEntityChangeLogInterceptor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.2-MA-04（plan 2502 Phase 7/分片 F）回归钉：nop-sys 双面账本保留期清理 + ChangeLog
 * saveDirectly 批量化。
 * <p>
 * 断言面（ledger-retention-policy §3.4 成员 6 行 + §3.2 nop-sys 行）：
 * <ul>
 * <li>① sys_lock 僵尸行（expireAt 过期且超保留窗+无人争用）周期清理断言（争用触发式惰性
 * 清理的零覆盖面）</li>
 * <li>② 在持有/未到期锁行保留断言（活跃锁随续约刷新 expireAt，清理不误伤活锁）</li>
 * <li>③ sys_change_log 按留存语义清理断言（缺省 90 天；0=永久保留显式 opt-in——零删除）</li>
 * <li>④ 批量化后列行数与内容等价断言（postSave/postUpdate 面：行数/propName/newValue 不变，
 * 单会话批量插入替代每列一会话写放大）</li>
 * <li>接线验证（rule 23）：doStart 生产生命周期链真实调度 + 被调度命令在真实 DB 链上删除
 * 过期行 + doStop 取消（防 C7-005 空壳）；显式禁用与 fail-fast 包络</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysLedgerRetention_MA_04 extends JunitBaseTestCase {
    private static final long DAY_MS = 24L * 3600_000L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    /**
     * ①+②：僵尸锁行（超保留窗过期+无人争用）被清理；活跃锁（未到期）与保留窗内过期锁保留
     * ——清理不误伤活锁，也不抢在保留窗之前删除（宁长勿短）。
     */
    @Test
    public void testMA_04_zombieLockPurgedHeldAndInWindowRetained() {
        long now = System.currentTimeMillis();
        seedLock("res-ma04-zombie", "grpA", now - 8 * DAY_MS);
        seedLock("res-ma04-held", "grpA", now + 60_000L);
        seedLock("res-ma04-in-window", "grpB", now - 1 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        SysDaoLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredLedgerRows(7 * DAY_MS, 90 * DAY_MS, 1000);
        assertEquals(1L, deleted, "only the beyond-retention zombie lock row must be purged");

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(lockDao().getEntityById(lockPk("res-ma04-zombie", "grpA")),
                "zombie lock row (expired holder + no contention) must be purged");
        assertNotNull(lockDao().getEntityById(lockPk("res-ma04-held", "grpA")),
                "held (unexpired) lock row must be retained - active lock never enters the predicate");
        assertNotNull(lockDao().getEntityById(lockPk("res-ma04-in-window", "grpB")),
                "expired-but-in-retention-window lock row must be retained (longer-is-safer)");
    }

    /**
     * ③：sys_change_log 按留存语义清理——缺省窗（90 天）外删除、窗内保留；
     * {@code changelogRetentionMillis=0} 为永久保留 opt-in：零删除（不允许三无状态延续，
     * 永久保留必须是显式声明形态）。
     */
    @Test
    public void testMA_04_changeLogRetentionSweepAndZeroPermanentOptIn() {
        String beyondSid = seedChangeLog("ma04-log-beyond", tsMinus(100 * DAY_MS));
        String withinSid = seedChangeLog("ma04-log-within", tsMinus(10 * DAY_MS));

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        SysDaoLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredLedgerRows(7 * DAY_MS, 90 * DAY_MS, 1000);
        assertEquals(1L, deleted, "only the beyond-90d audit row must be purged");

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(changelogDao().getEntityById(beyondSid), "beyond-retention audit row must be purged");
        assertNotNull(changelogDao().getEntityById(withinSid), "in-window audit row must be retained");

        // 0 = 永久保留显式 opt-in：该面零删除
        String permanentSid = seedChangeLog("ma04-log-permanent", tsMinus(400 * DAY_MS));
        ormTemplate.flushSession();
        ormTemplate.clearSession();

        long deletedZero = cleaner.purgeExpiredLedgerRows(7 * DAY_MS, 0, 1000);
        assertEquals(0L, deletedZero, "changelog retention 0 (permanent opt-in) must delete nothing");
        assertNotNull(changelogDao().getEntityById(permanentSid),
                "permanent opt-in must keep audit rows regardless of age");
    }

    /**
     * 清理幂等：重复触发无害，第二轮零删除。
     */
    @Test
    public void testMA_04_purgeIdempotent() {
        long now = System.currentTimeMillis();
        seedLock("res-ma04-idem", "grpIdem", now - 8 * DAY_MS);
        seedChangeLog("ma04-log-idem", tsMinus(100 * DAY_MS));

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        SysDaoLedgerCleaner cleaner = newCleaner();
        long first = cleaner.purgeExpiredLedgerRows(7 * DAY_MS, 90 * DAY_MS, 1000);
        assertEquals(2L, first, "first round must delete the zombie lock + expired audit row");
        long second = cleaner.purgeExpiredLedgerRows(7 * DAY_MS, 90 * DAY_MS, 1000);
        assertEquals(0L, second, "second round must be a no-op (idempotent predicate)");
    }

    /**
     * 接线验证（rule 23）：doStart 生产生命周期链真实调度清理链（taskName/interval 断言），
     * 被调度命令运行时清理入口在真实 DB 链上生效（僵尸锁行经 scheduled command 删除——
     * 非仅 API/bean 存在，防 C7-005 空壳）；doStop 取消。
     */
    @Test
    public void testMA_04_lifecycleChainInvokesLedgerCleanup() {
        seedLock("res-ma04-wiring", "grpWiring", System.currentTimeMillis() - 8 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        CapturingTimer timer = new CapturingTimer();
        SysDaoLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLockRetentionDays(7);
        cleaner.setChangelogRetentionDays(90);
        cleaner.setLedgerCleanupInterval(java.time.Duration.ofHours(1));
        cleaner.setLedgerDeleteBatchSize(500);

        cleaner.start();

        assertEquals(SysDaoLedgerCleaner.TASK_NAME, timer.taskName.get(),
                "wiring: cleaner lifecycle chain must schedule the ledger cleanup chain");
        assertEquals(3600_000L, timer.delayMillis.get(), "cleanup interval passed to scheduler");

        // 触发被调度的清理命令本体：清理入口在真实 DB 链上生效
        timer.command.get().run();

        ormTemplate.flushSession();
        ormTemplate.clearSession();
        assertNull(lockDao().getEntityById(lockPk("res-ma04-wiring", "grpWiring")),
                "zombie lock must be purged through the scheduled cleanup command (wiring chain)");

        cleaner.stop();
        assertTrue(timer.cancelled.get(), "doStop must cancel the cleanup chain");
    }

    /**
     * 双 face 保留期均为 0 时不调度（lock 0=禁用；changelog 0=永久 opt-in——无行可清）。
     */
    @Test
    public void testMA_04_zeroRetentionDisablesCleanupScheduling() {
        CapturingTimer timer = new CapturingTimer();
        SysDaoLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLockRetentionDays(0);
        cleaner.setChangelogRetentionDays(0);

        cleaner.start();

        assertNull(timer.taskName.get(), "both faces at 0 must disable cleanup scheduling");
        cleaner.stop();
    }

    /**
     * fail-fast 包络（设计 §3.1 第 5 项）：错配置启动期拒绝，不降级继续。
     */
    @Test
    public void testMA_04_invalidLedgerConfigFailsFast() {
        SysDaoLedgerCleaner negativeLock = newCleaner();
        negativeLock.setTimer(new CapturingTimer());
        negativeLock.setWorker(Runnable::run);
        negativeLock.setLockRetentionDays(-1);
        NopException ex1 = assertThrows(NopException.class, negativeLock::start,
                "negative lock retention must fail fast at startup");
        assertEquals("nop.err.sys.ledger-config-invalid", ex1.getErrorCode());

        SysDaoLedgerCleaner negativeChangelog = newCleaner();
        negativeChangelog.setTimer(new CapturingTimer());
        negativeChangelog.setWorker(Runnable::run);
        negativeChangelog.setChangelogRetentionDays(-1);
        NopException ex2 = assertThrows(NopException.class, negativeChangelog::start,
                "negative changelog retention must fail fast at startup");
        assertEquals("nop.err.sys.ledger-config-invalid", ex2.getErrorCode());

        SysDaoLedgerCleaner zeroBatch = newCleaner();
        zeroBatch.setTimer(new CapturingTimer());
        zeroBatch.setWorker(Runnable::run);
        zeroBatch.setLedgerDeleteBatchSize(0);
        NopException ex3 = assertThrows(NopException.class, zeroBatch::start,
                "non-positive batch cap must fail fast at startup");
        assertEquals("nop.err.sys.ledger-config-invalid", ex3.getErrorCode());

        SysDaoLedgerCleaner tinyInterval = newCleaner();
        tinyInterval.setTimer(new CapturingTimer());
        tinyInterval.setWorker(Runnable::run);
        tinyInterval.setLedgerCleanupInterval(java.time.Duration.ofSeconds(30));
        NopException ex4 = assertThrows(NopException.class, tinyInterval::start,
                "cleanup interval below 60s floor must fail fast at startup");
        assertEquals("nop.err.sys.ledger-config-invalid", ex4.getErrorCode());
    }

    // ==================== ④ ChangeLog saveDirectly 批量化（行数/内容等价 + 单会话） ====================

    /**
     * 批量化等价断言：postSave 两个可审计列 → 恰好 1 个独立会话、2 行审计、单次 flushActionQueue
     * （此前为每列一个会话的 N 会话写放大）；propName/newValue 与既有逐列语义逐行等价；
     * postUpdate（脏列剔除 version）、postDelete（deleted=0->1 单行）同面。
     */
    @Test
    public void testMA_04_changeLogBatchSaveEquivalenceInSingleSession() {
        RecordingInterceptor interceptor = newRecordingInterceptor();
        IOrmEntity entity = entity(interceptor.model, "id-ma04", currentValues(),
                interceptor.valuesByName, new LinkedHashMap<>());

        interceptor.postSave(entity);

        assertEquals(1, interceptor.sessionCount,
                "postSave must persist all column rows in exactly one independent session"
                        + " (was one session per column)");
        assertEquals(1, interceptor.flushCount, "exactly one batched flush per entity event");
        assertEquals(2, interceptor.saved.size(), "row count equivalence: one row per auditable column");
        assertEquals("wi7_name", interceptor.saved.get(0).getPropName());
        assertEquals("current-name", interceptor.saved.get(0).getNewValue());
        assertEquals("secret_col", interceptor.saved.get(1).getPropName());
        assertEquals("current-secret", interceptor.saved.get(1).getNewValue(),
                "content equivalence: per-column newValue semantics unchanged");
        assertEquals("save", interceptor.saved.get(0).getOperationName());

        // postUpdate：脏列剔除 version，单会话批量
        RecordingInterceptor upd = newRecordingInterceptor();
        LinkedHashMap<Integer, Object> dirty = new LinkedHashMap<>();
        dirty.put(PROP_ID_NAME, "old-name");
        dirty.put(PROP_ID_VER, 0L);
        IOrmEntity updEntity = entity(upd.model, "id-ma04-u", currentValues(), upd.valuesByName, dirty);
        upd.postUpdate(updEntity);

        assertEquals(1, upd.sessionCount, "postUpdate must persist in exactly one session");
        assertEquals(1, upd.flushCount);
        assertEquals(1, upd.saved.size(), "dirty-column rows only (version excluded)");
        assertEquals("wi7_name", upd.saved.get(0).getPropName());
        assertEquals("old-name", upd.saved.get(0).getOldValue());
        assertEquals("current-name", upd.saved.get(0).getNewValue());
        assertEquals("update", upd.saved.get(0).getOperationName());

        // postDelete：单行 deleted=0->1
        RecordingInterceptor del = newRecordingInterceptor();
        IOrmEntity delEntity = entity(del.model, "id-ma04-d", currentValues(), del.valuesByName,
                new LinkedHashMap<>());
        del.postDelete(delEntity);

        assertEquals(1, del.sessionCount);
        assertEquals(1, del.saved.size());
        assertEquals(NopSysDaoConstants.PROP_DELETED, del.saved.get(0).getPropName());
        assertEquals("0", del.saved.get(0).getOldValue());
        assertEquals("1", del.saved.get(0).getNewValue());
    }

    static final int PROP_ID_NAME = 1;
    static final int PROP_ID_SECRET = 3;
    static final int PROP_ID_VER = 99;

    /** 批量化记录桩：记录会话数、flush 次数与批量落库行 */
    static class RecordingInterceptor extends OrmEntityChangeLogInterceptor {
        final List<NopSysChangeLog> saved = new ArrayList<>();
        int sessionCount;
        int flushCount;
        IEntityModel model;
        Map<String, Object> valuesByName = new java.util.HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        protected IOrmEntityDao<NopSysChangeLog> dao() {
            return stub(IOrmEntityDao.class, name -> {
                if ("newEntity".equals(name))
                    return (m1, a1) -> new NopSysChangeLog();
                if ("getOrmTemplate".equals(name))
                    return (m1, a1) -> stub(IOrmTemplate.class, tplMethod -> {
                        if ("runInNewSession".equals(tplMethod))
                            return (m2, a2) -> {
                                Function<IOrmSession, ?> fn = (Function<IOrmSession, ?>) a2[0];
                                sessionCount++;
                                return fn.apply(stub(IOrmSession.class, sessMethod -> {
                                    if ("saveDirectly".equals(sessMethod))
                                        return (m3, a3) -> {
                                            saved.add((NopSysChangeLog) a3[0]);
                                            assertFalse((Boolean) a3[1],
                                                    "batched rows must defer queue flush (flushActionQueue=false)");
                                            return null;
                                        };
                                    if ("flushActionQueue".equals(sessMethod))
                                        return (m3, a3) -> {
                                            flushCount++;
                                            return null;
                                        };
                                    return null;
                                }));
                            };
                        return null;
                    });
                return null;
            });
        }
    }

    static RecordingInterceptor newRecordingInterceptor() {
        RecordingInterceptor interceptor = new RecordingInterceptor();
        interceptor.model = entityModel("test.Ma04AuditedEntity",
                Arrays.asList(OrmModelConstants.TAG_AUDIT_SAVE, OrmModelConstants.TAG_AUDIT),
                PROP_ID_VER,
                Arrays.asList(column("wi7_name", PROP_ID_NAME),
                        column("ver", PROP_ID_VER),
                        column("secret_col", PROP_ID_SECRET)),
                "wi7_name");
        interceptor.valuesByName.put("wi7_name", "BIZ-MA04");
        return interceptor;
    }

    static Map<Integer, Object> currentValues() {
        Map<Integer, Object> values = new java.util.HashMap<>();
        values.put(PROP_ID_NAME, "current-name");
        values.put(PROP_ID_VER, 1L);
        values.put(PROP_ID_SECRET, "current-secret");
        return values;
    }

    // ==================== 反射代理桩基建 ====================

    @SuppressWarnings("unchecked")
    static <T> T stub(Class<T> type, Function<String, BiFunction<String, Object[], Object>> dispatcher) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> {
                    BiFunction<String, Object[], Object> handler = dispatcher.apply(method.getName());
                    if (handler != null)
                        return handler.apply(method.getName(), args);
                    if (method.getReturnType() == boolean.class)
                        return false;
                    if (method.getReturnType() == int.class)
                        return 0;
                    return null;
                });
    }

    static IColumnModel column(String name, int propId) {
        return stub(IColumnModel.class, methodName -> {
            switch (methodName) {
                case "getName":
                    return (m, a) -> name;
                case "getPropId":
                    return (m, a) -> propId;
                case "containsTag":
                    return (m, a) -> false;
                default:
                    return null;
            }
        });
    }

    static IEntityModel entityModel(String name, List<String> tags, int versionPropId,
                                    List<IColumnModel> columns, String bizKeyProp) {
        Map<Integer, IColumnModel> byPropId = new java.util.HashMap<>();
        for (IColumnModel col : columns)
            byPropId.put(col.getPropId(), col);
        return stub(IEntityModel.class, methodName -> {
            switch (methodName) {
                case "getName":
                    return (m, a) -> name;
                case "getShortName":
                    return (m, a) -> name.substring(name.lastIndexOf('.') + 1);
                case "containsTag":
                    return (m, a) -> tags.contains(a[0]);
                case "getVersionPropId":
                    return (m, a) -> versionPropId;
                case "getColumns":
                    return (m, a) -> columns;
                case "getColumnByPropId":
                    return (m, a) -> byPropId.get((Integer) a[0]);
                case "prop_get":
                    return (m, a) -> OrmModelConstants.ORM_BIZ_KEY_PROP.equals(a[0]) ? bizKeyProp : null;
                default:
                    return null;
            }
        });
    }

    static IOrmEntity entity(IEntityModel model, String idString, Map<Integer, Object> valuesByPropId,
                             Map<String, Object> valuesByName, LinkedHashMap<Integer, Object> dirtyProps) {
        return stub(IOrmEntity.class, methodName -> {
            switch (methodName) {
                case "orm_entityModel":
                    return (m, a) -> model;
                case "orm_idString":
                    return (m, a) -> idString;
                case "orm_propValue":
                    return (m, a) -> valuesByPropId.get((Integer) a[0]);
                case "orm_propValueByName":
                    return (m, a) -> valuesByName.get((String) a[0]);
                case "orm_forEachDirtyProp":
                    return (m, a) -> {
                        java.util.function.ObjIntConsumer<Object> consumer =
                                (java.util.function.ObjIntConsumer<Object>) a[0];
                        dirtyProps.forEach((propId, value) -> consumer.accept(value, propId));
                        return null;
                    };
                default:
                    return null;
            }
        });
    }

    // ==================== 捕获调度参数的 timer（wiring 断言载体） ====================

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

    private SysDaoLedgerCleaner newCleaner() {
        SysDaoLedgerCleaner cleaner = new SysDaoLedgerCleaner();
        cleaner.setDaoProvider(daoProvider);
        return cleaner;
    }

    private static Timestamp tsMinus(long offsetMillis) {
        return new Timestamp(System.currentTimeMillis() - offsetMillis);
    }

    /** nop_sys_lock 复合主键载体（与 SysDaoResourceLockManager castId 同构：group/name） */
    private io.nop.orm.support.OrmCompositePk lockPk(String lockName, String lockGroup) {
        io.nop.sys.dao.entity._gen.NopSysLockPkBuilder builder = new io.nop.sys.dao.entity._gen.NopSysLockPkBuilder();
        builder.setLockName(lockName);
        builder.setLockGroup(lockGroup);
        return builder.build();
    }

    private void seedLock(String lockName, String lockGroup, long expireAt) {
        NopSysLock e = lockDao().newEntity();
        e.setLockName(lockName);
        e.setLockGroup(lockGroup);
        e.setLockTime(new Timestamp(System.currentTimeMillis()));
        e.setExpireAt(new Timestamp(expireAt));
        e.setHolderId("holder-ma04");
        e.setHolderAdder("127.0.0.1");
        e.setAppId("test-app");
        e.setLockReason("ma04-test");
        lockDao().saveEntityDirectly(e);
    }

    private String seedChangeLog(String sid, Timestamp changeTime) {
        NopSysChangeLog e = changelogDao().newEntity();
        e.setSid(sid);
        e.setBizObjName("TestObj");
        e.setObjId("obj-ma04");
        e.setOperationName("save");
        e.setPropName("testProp");
        e.setNewValue("v-ma04");
        e.setChangeTime(changeTime);
        e.setOperatorId("system");
        changelogDao().saveEntityDirectly(e);
        return sid;
    }

    private IEntityDao<NopSysLock> lockDao() {
        return daoProvider.daoFor(NopSysLock.class);
    }

    private IEntityDao<NopSysChangeLog> changelogDao() {
        return daoProvider.daoFor(NopSysChangeLog.class);
    }
}
