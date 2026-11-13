package io.nop.config.source.jdbc;

import io.nop.commons.util.objects.ValueWithLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2497 Phase 2（2.2-C4-001）回归：JdbcConfigSource 刷新链卸载——
 * globalTimer 只承载调度触发（调度槽），刷新任务体（无超时 JDBC 全量查询 = 阻塞 I/O）
 * 经 {@code executeOn(globalWorker())} hop 到 globalWorker 执行（与 AbstractFileConfigSource 同口径）。
 * <p>
 * 同时自证绝缘正交性：C1-021 的 per-callback 隔离（plan 2496）在卸载后的 worker 线程执行路径上
 * 依然生效——坏回调不杀死刷新链、不拖累同轮好回调；失败计数语义不因卸载改变。
 */
public class TestJdbcConfigSourceWorkerOffload {

    static final String WORKER_PREFIX = "nop-global-worker";
    static final String TIMER_PREFIX = "nop-global-timer";

    /**
     * 内存行数据源（key/value 两列），测试中途修改以制造 isChanged。
     */
    static final AtomicReference<Map<String, String>> ROWS = new AtomicReference<>(new TreeMap<>(Map.of("k1", "v1")));

    /**
     * 刷新任务体（executeQuery）的实际执行线程——由 fake Statement 在查询时刻记录。
     */
    static final AtomicReference<String> QUERY_THREAD = new AtomicReference<>();

    /**
     * 语句级 queryTimeout 传导观测——fake Statement 记录 setQueryTimeout 实参（rule 23 接线断言）。
     */
    static final AtomicReference<Integer> APPLIED_QUERY_TIMEOUT = new AtomicReference<>();

    /**
     * 调度轮次武装标记：构造期初始装载发生在测试主线程（非调度轮次），
     * 武装后的 executeQuery 才记录执行线程。
     */
    static final AtomicBoolean ARMED = new AtomicBoolean(false);

    @org.junit.jupiter.api.BeforeEach
    void reset() {
        ROWS.set(new TreeMap<>(Map.of("k1", "v1")));
        QUERY_THREAD.set(null);
        APPLIED_QUERY_TIMEOUT.set(null);
        ARMED.set(false);
    }

    static Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("createStatement")) {
                        return fakeStatement();
                    }
                    if (name.equals("close") || name.equals("isClosed")) {
                        return name.equals("isClosed") ? Boolean.FALSE : null;
                    }
                    return null;
                });
    }

    static Statement fakeStatement() {
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("setQueryTimeout")) {
                        APPLIED_QUERY_TIMEOUT.set((Integer) args[0]);
                        return null;
                    }
                    if (name.equals("executeQuery")) {
                        if (ARMED.get()) {
                            QUERY_THREAD.compareAndSet(null, Thread.currentThread().getName());
                        }
                        return fakeResultSet(new TreeMap<>(ROWS.get()));
                    }
                    return null;
                });
    }

    static ResultSet fakeResultSet(Map<String, String> rows) {
        AtomicInteger index = new AtomicInteger(-1);
        List<String> keys = new CopyOnWriteArrayList<>(rows.keySet());
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("next")) {
                        return index.incrementAndGet() < keys.size();
                    }
                    if (name.equals("getString")) {
                        int col = (Integer) args[0];
                        String key = keys.get(index.get());
                        return col == 1 ? key : rows.get(key);
                    }
                    return null;
                });
    }

    static JdbcConfigSource newSource(Duration refreshInterval) {
        JdbcConfig config = new JdbcConfig() {
            @Override
            public Connection createConnection() {
                return fakeConnection();
            }
        };
        config.setSelectAllQuery("select name, value from t");
        config.setRefreshInterval(refreshInterval);
        return new JdbcConfigSource(config);
    }

    private static boolean await(BooleanSupplier cond, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean())
                return true;
            Thread.sleep(50);
        }
        return cond.getAsBoolean();
    }

    /**
     * 端到端链路（rule 22 + rule 23 接线断言）：构造（初始装载）→ 注册 globalTimer 载体 → 调度刷新轮次 →
     * 任务体（executeQuery）在 globalWorker 线程执行 → 变更快照回写 → 变更回调触发 →
     * 坏回调（C1-021 形态）被 per-callback 隔离、同轮好回调仍执行、下一轮刷新链仍活着。
     * 卸载与回调隔离共存的完整链路在真实 globalTimer/globalWorker 单例上验证。
     */
    @Test
    public void testOffloadedRefreshChainKeepsCallbackIsolation_C4_001() throws Exception {
        JdbcConfigSource source = newSource(Duration.ofSeconds(1));
        try {
            AtomicInteger goodCalls = new AtomicInteger();
            AtomicReference<String> callbackThread = new AtomicReference<>();
            source.addOnChange(goodCalls::incrementAndGet);
            source.addOnChange(() -> {
                callbackThread.compareAndSet(null, Thread.currentThread().getName());
                // C1-021 注入形态（RuntimeException 族）：坏回调必须被隔离，不得杀死卸载后的刷新链
                throw new io.nop.api.core.exceptions.NopException(
                        io.nop.config.ConfigErrors.ERR_CONFIG_INVALID_CONFIG_BEAN_NAME);
            });

            // 构造期初始装载 k1=v1；武装 + 置 v2 → 首个调度轮次 isChanged 成立 → 回调链触发
            ROWS.set(new TreeMap<>(Map.of("k1", "v2")));
            ARMED.set(true);

            assertTrue(await(() -> goodCalls.get() > 0, 15_000),
                    "配置变更必须经卸载后的刷新链触发好回调（端到端链路闭合）");

            // 接线断言：刷新任务体（executeQuery）运行在 globalWorker 池线程，不在 timer 线程
            String queryThreadName = QUERY_THREAD.get();
            assertTrue(queryThreadName != null && queryThreadName.startsWith(WORKER_PREFIX),
                    "JDBC 刷新任务体必须 hop 到 globalWorker 执行，实际线程: " + queryThreadName);
            assertFalse(queryThreadName != null && queryThreadName.startsWith(TIMER_PREFIX),
                    "JDBC 刷新任务体不得运行在 globalTimer 线程（DB 阻塞占死调度槽），实际线程: " + queryThreadName);

            // 回调随任务体在 worker 线程执行（回调由刷新轮次内联触发）
            String callbackThreadName = callbackThread.get();
            assertTrue(callbackThreadName != null && callbackThreadName.startsWith(WORKER_PREFIX),
                    "变更回调随卸载任务体在 globalWorker 上执行，实际线程: " + callbackThreadName);

            // C1-021 正交性：坏回调抛出后，下一轮刷新仍触发好回调（刷新链未死、隔离语义未因卸载改变）
            int before = goodCalls.get();
            ROWS.set(new TreeMap<>(Map.of("k1", "v3")));
            assertTrue(await(() -> goodCalls.get() > before, 15_000),
                    "坏回调被隔离后下一轮刷新必须仍触发好回调（刷新链存活，C1-021 语义不因卸载回退）");
        } finally {
            source.close();
        }
    }

    /**
     * 语句级 queryTimeout 传导接线断言（rule 23）：真实 loadConfig 路径上，刷新查询执行前
     * 必须把超时参数传导到 Statement（默认配置 = 有限超时，DB 分区从「无限期占死」退化为
     * 「周期性失败重试」的行为钉）。
     */
    @Test
    public void testLoadConfigConductsQueryTimeoutToStatement_C4_001() throws Exception {
        JdbcConfigSource source = newSource(null);
        try {
            // 构造期即执行一次 loadConfig（refreshInterval=null 无调度）→ fake Statement 已被施超时
            Integer applied = APPLIED_QUERY_TIMEOUT.get();
            assertTrue(applied != null && applied > 0,
                    "刷新查询必须向 Statement 传导有限 queryTimeout（默认配置即生效），实际: " + applied);
        } finally {
            source.close();
        }
    }
}
