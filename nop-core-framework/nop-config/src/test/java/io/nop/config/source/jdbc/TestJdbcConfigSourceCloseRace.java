package io.nop.config.source.jdbc;

import io.nop.api.core.exceptions.NopException;
import io.nop.config.ConfigErrors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2506 2.2-C1-009（←C4-002）回归：JdbcConfigSource.close() 与在途刷新无互斥——
 * 停机撞在途刷新（任务体经 executeOn 在 globalWorker 执行，R1-02 形态）时，在途轮的
 * 连接重建写入字段后刷新任务已 cancel(false) 不等待，新建连接永不关闭
 * （每次「停机×刷新在途」泄漏一条直连）。
 * <p>
 * 修复裁定④（设计 plugin-lifecycle-and-config-source-hardening.md §3.4）= close() 加
 * synchronized（与 refreshConfig/tryTriggerChange 同 this monitor）+ loadConfig 重建连接点
 * 前置 closed 守卫（类型化抛错，新码落 ConfigErrors；monitor 全量串行下生产不可达，
 * 为锁纪律的防退化守卫）。C1-021 per-callback 隔离体与 R1-02 卸载/超时语义零触碰。
 */
public class TestJdbcConfigSourceCloseRace {

    /**
     * 内存行数据源（key/value 两列），测试中途修改以驱动变更回调。
     */
    static final AtomicReference<Map<String, String>> ROWS = new AtomicReference<>(
            new TreeMap<>(Map.of("k1", "v1")));

    @BeforeEach
    public void resetRows() {
        ROWS.set(new TreeMap<>(Map.of("k1", "v1")));
    }

    /**
     * 竞态观测桩：createConnection 计数并为每条连接记录 close 次数；armed 后首个 executeQuery
     * 在查询点阻塞于 gate（放行或 2s 有界超时自释）并注入 SQLException，强制在途轮走
     * 「关旧连接→重建」路径。
     */
    static class RaceJdbcConfig extends JdbcConfig {
        final AtomicInteger createdConnections = new AtomicInteger();
        final List<AtomicInteger> closeCounts = new CopyOnWriteArrayList<>();
        volatile CountDownLatch queryEntered;
        volatile CountDownLatch gate;
        final AtomicBoolean armed = new AtomicBoolean(false);

        @Override
        public Connection createConnection() throws SQLException {
            createdConnections.incrementAndGet();
            AtomicInteger closedCount = new AtomicInteger();
            closeCounts.add(closedCount);
            return raceConnection(closedCount);
        }

        private Connection raceConnection(AtomicInteger closedCount) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("createStatement")) {
                            return raceStatement();
                        }
                        if (name.equals("close")) {
                            closedCount.incrementAndGet();
                            return null;
                        }
                        if (name.equals("isClosed")) {
                            return Boolean.FALSE;
                        }
                        return null;
                    });
        }

        private Statement raceStatement() {
            return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                    new Class[]{Statement.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("setQueryTimeout")) {
                            return null;
                        }
                        if (name.equals("executeQuery")) {
                            if (armed.compareAndSet(true, false)) {
                                CountDownLatch entered = queryEntered;
                                CountDownLatch gateLatch = gate;
                                if (entered != null)
                                    entered.countDown();
                                if (gateLatch != null) {
                                    // 有界等待：修复形态下 close 在 this monitor 上等待在途轮，
                                    // 主线程在 close 返回前无法放行——2s 超时自释防测试死锁
                                    gateLatch.await(2, TimeUnit.SECONDS);
                                }
                                throw new SQLException("nop-test: injected in-flight query failure");
                            }
                            return raceResultSet(new TreeMap<>(ROWS.get()));
                        }
                        return null;
                    });
        }

        private ResultSet raceResultSet(Map<String, String> rows) {
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
    }

    private static RaceJdbcConfig newRaceConfig() {
        RaceJdbcConfig config = new RaceJdbcConfig();
        config.setSelectAllQuery("select name, value from t");
        config.setRefreshInterval(Duration.ofSeconds(1));
        return config;
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

    private static void invokeRefresh(JdbcConfigSource source) throws Exception {
        Method refresh = JdbcConfigSource.class.getDeclaredMethod("refreshConfig");
        refresh.setAccessible(true);
        try {
            refresh.invoke(source);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            if (cause instanceof Exception)
                throw (Exception) cause;
            throw e;
        }
    }

    /**
     * 竞态腿（修复前红）：停机撞在途刷新门闩对齐交错——在途轮通过 closed 检查后在查询点阻塞
     * （持有 this monitor），主线程 close：修复前不互斥，置位 + 关旧连接 + cancel(false) 后，
     * 在途轮放行走重建路径，新连接写入字段后任务已取消、永不关闭（泄漏）；修复后 close 在
     * monitor 上等待在途轮完成，两序全序化，重建出的连接由 close 关闭（零泄漏）。
     */
    @Test
    @Timeout(30)
    public void testCloseDoesNotLeakConnectionRebuiltByInFlightRefresh() throws Exception {
        RaceJdbcConfig config = newRaceConfig();
        JdbcConfigSource source = new JdbcConfigSource(config); // 构造期装载成功（连接 #1）
        try {
            // 武装首个调度轮次：到达查询点后阻塞于门闩、放行/超时后注入失败（强制重建路径）
            config.queryEntered = new CountDownLatch(1);
            config.gate = new CountDownLatch(1);
            config.armed.set(true);

            assertTrue(config.queryEntered.await(10, TimeUnit.SECONDS),
                    "在途刷新轮必须到达查询点（门闩对齐，此时任务体已持有 this monitor）");

            // 主线程停机：与持有 monitor 的在途轮交错（修复后 close 阻塞至该轮完成）
            source.close();

            // 放行在途轮（修复形态下该轮已超时自释完成，此处为无害 no-op）
            config.gate.countDown();

            // 等待在途轮完成重建路径
            assertTrue(await(() -> config.createdConnections.get() >= 2, 8000),
                    "在途刷新轮必须完成重建路径以暴露交错窗");

            // 修复后形态：close 与在途轮全序化——重建出的新连接必须已被关闭（零泄漏）
            // 修复前红：新连接写入字段后任务已 cancel，close 计数恒为 0（永不被关闭）
            assertEquals(1, config.closeCounts.get(1).get(),
                    "close 后在途刷新轮重建的新连接必须已被关闭（close 互斥，停机零连接泄漏）");
        } finally {
            source.close();
        }
    }

    /**
     * closed 守卫防退化腿（rule 24 类型化抛错）：直接构造 closed 状态（closed=true 且连接已关、
     * 字段置空）调用重建路径——守卫必须在 createConnection 之前命中并抛 ConfigErrors 新码的
     * 类型化异常，而非静默重建。修复前红：无守卫，重建路径照常建连成功返回（零异常）。
     */
    @Test
    @Timeout(30)
    public void testClosedGuardRejectsConnectionRebuildWithTypedError() throws Exception {
        RaceJdbcConfig config = newRaceConfig();
        JdbcConfigSource source = new JdbcConfigSource(config);
        source.close();

        // 直接构造 closed 终态：closed 置位、连接字段置空（重建点前守卫的防御对象）
        Field closedField = JdbcConfigSource.class.getDeclaredField("closed");
        closedField.setAccessible(true);
        assertEquals(Boolean.TRUE, closedField.get(source), "close 后 closed 必须置位");

        Field connectionField = JdbcConfigSource.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(source, null);

        assertEquals(1, config.createdConnections.get());
        assertEquals(1, config.closeCounts.get(0).get(), "close 必须已关闭既有连接");

        Method loadConfig = JdbcConfigSource.class.getDeclaredMethod("loadConfig");
        loadConfig.setAccessible(true);

        NopException e = assertThrows(NopException.class, () -> {
            try {
                loadConfig.invoke(source);
            } catch (InvocationTargetException ite) {
                throw NopException.adapt(ite.getCause());
            }
        });
        assertEquals(ConfigErrors.ERR_CONFIG_SOURCE_CLOSED.getErrorCode(), e.getErrorCode(),
                "closed 守卫必须抛类型化异常（新码落 ConfigErrors），不得静默 return");
        assertEquals(1, config.createdConnections.get(),
                "closed 守卫必须在连接重建点之前命中（close 后不再 createConnection）");
    }

    /**
     * 端到端链路②（rule 22，配置源全生命周期）：构造成功 → 显式刷新（真实 refreshConfig 管线）
     * → 变更回调触达 → close → 连接关闭 + 任务取消 + 无泄漏（close 后超过一个调度周期观测
     * 零新回调、零新连接）。
     */
    @Test
    @Timeout(30)
    public void testConfigSourceLifecycleEndToEnd_NoLeakAfterClose() throws Exception {
        RaceJdbcConfig config = newRaceConfig();
        JdbcConfigSource source = new JdbcConfigSource(config);
        try {
            AtomicInteger callbacks = new AtomicInteger();
            source.addOnChange(callbacks::incrementAndGet);

            // 显式刷新：值变更 → 回调触达
            ROWS.set(new TreeMap<>(Map.of("k1", "v2")));
            invokeRefresh(source);
            assertTrue(callbacks.get() >= 1, "显式刷新的配置变更必须触达变更回调");

            // close：连接关闭 + 任务取消
            source.close();

            // close 后观测窗（远超一个调度周期 1s）：变更不再触发回调、零新连接（任务取消、无泄漏）
            int callbacksAtClose = callbacks.get();
            int createdAtClose = config.createdConnections.get();
            ROWS.set(new TreeMap<>(Map.of("k1", "v3")));
            invokeRefresh(source); // closed 后 refreshConfig 入口检查退出：变更不再触达回调
            Thread.sleep(2200);

            assertEquals(callbacksAtClose, callbacks.get(), "close 后变更回调不得再触发（任务取消）");
            assertEquals(createdAtClose, config.createdConnections.get(), "close 后零新连接（无泄漏）");
            assertEquals(1, config.closeCounts.get(0).get(), "close 必须关闭已建连接");
        } finally {
            source.close();
        }
    }
}
