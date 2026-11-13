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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2496 2.2-C1-021 回归：JdbcConfigSource.tryTriggerChange 的 per-callback 隔离——
 * 单个坏回调（RuntimeException / Error）不杀死 DB 配置热更新刷新链，不拖累同轮其余回调，
 * 下一轮刷新仍触发回调。
 */
public class TestJdbcConfigSourceCallbackIsolation {

    /**
     * 内存行数据源（key/value 两列），可在测试中途修改以制造 isChanged。
     */
    static final AtomicReference<Map<String, String>> ROWS = new AtomicReference<>(new TreeMap<>(Map.of("k1", "v1")));

    @org.junit.jupiter.api.BeforeEach
    void resetRows() {
        ROWS.set(new TreeMap<>(Map.of("k1", "v1")));
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
                    if (method.getName().equals("executeQuery")) {
                        return fakeResultSet(new TreeMap<>(ROWS.get()));
                    }
                    if (method.getName().equals("close")) {
                        return null;
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
                    if (name.equals("close")) {
                        return null;
                    }
                    return null;
                });
    }

    static JdbcConfigSource newSource() {
        JdbcConfig config = new JdbcConfig() {
            @Override
            public Connection createConnection() {
                return fakeConnection();
            }
        };
        config.setSelectAllQuery("select name, value from t");
        config.setRefreshInterval(Duration.ofSeconds(3600)); // 让 addOnChange 生效；实际刷新由测试显式驱动
        return new JdbcConfigSource(config);
    }

    private static Map<String, ValueWithLocation> toVars(Map<String, String> rows) {
        Map<String, ValueWithLocation> vars = new TreeMap<>();
        rows.forEach((k, v) -> vars.put(k, ValueWithLocation.of(null, v)));
        return vars;
    }

    /**
     * C1-021 主用例：注册抛 RuntimeException 的坏回调后，同轮好回调仍执行、下一轮刷新仍触发
     * （刷新链不死）。
     */
    @Test
    public void testBadCallbackDoesNotKillRefreshChainNorBlockOthers() {
        JdbcConfigSource source = newSource();
        AtomicInteger goodCalls = new AtomicInteger();
        source.addOnChange(goodCalls::incrementAndGet);
        source.addOnChange(() -> {
            // NopException extends RuntimeException：plan C1-021 注入形态（RuntimeException 族）
            throw new io.nop.api.core.exceptions.NopException(io.nop.config.ConfigErrors.ERR_CONFIG_INVALID_CONFIG_BEAN_NAME);
        });

        // 第一轮：值变更触发回调；坏回调抛 RuntimeException 被隔离
        assertDoesNotThrow(() -> source.tryTriggerChange(toVars(Map.of("k1", "v2"))));
        assertEquals(1, goodCalls.get(), "good callback in the same round must still run");

        // 第二轮（下一轮刷新）：刷新链仍触发回调——原缺陷 = 坏回调零日志永久杀死全链
        assertDoesNotThrow(() -> source.tryTriggerChange(toVars(Map.of("k1", "v3"))));
        assertEquals(2, goodCalls.get(), "next refresh round must still trigger callbacks (chain alive)");

        source.close();
    }

    /**
     * Error 级 Throwable 同样被隔离（捕获边界 = Throwable，与平台绝缘管线对齐）。
     */
    @Test
    public void testErrorCallbackIsolatedAndChainAlive() {
        JdbcConfigSource source = newSource();
        AtomicInteger goodCalls = new AtomicInteger();
        source.addOnChange(goodCalls::incrementAndGet);
        source.addOnChange(() -> {
            throw new LinkageError("callback-error-boom");
        });

        assertDoesNotThrow(() -> source.tryTriggerChange(toVars(Map.of("k1", "v2"))));
        assertEquals(1, goodCalls.get());
        assertDoesNotThrow(() -> source.tryTriggerChange(toVars(Map.of("k1", "v3"))));
        assertEquals(2, goodCalls.get(), "refresh chain must survive an Error-level callback");

        source.close();
    }

    /**
     * 未变更时回调不触发（isChanged 守卫语义不回归）。
     */
    @Test
    public void testUnchangedVarsDoNotTriggerCallbacks() {
        JdbcConfigSource source = newSource();
        AtomicInteger goodCalls = new AtomicInteger();
        source.addOnChange(goodCalls::incrementAndGet);

        Map<String, ValueWithLocation> same = toVars(new TreeMap<>(Map.of("k1", "v1")));
        source.tryTriggerChange(same);
        source.tryTriggerChange(same);
        assertEquals(0, goodCalls.get(), "unchanged values must not trigger callbacks");

        source.close();
    }

    /**
     * 真实 refreshConfig 管线（timer 驱动）与坏回调协同：回调坏掉不毁掉 loadConfig 的
     * errorCount<10 抑制语义（既有语义保留）。
     */
    @Test
    public void testRefreshConfigPipelineSurvivesBadCallback() throws Exception {
        JdbcConfigSource source = newSource();
        AtomicInteger goodCalls = new AtomicInteger();
        source.addOnChange(goodCalls::incrementAndGet);
        source.addOnChange(() -> {
            // NopException extends RuntimeException：plan C1-021 注入形态（RuntimeException 族）
            throw new io.nop.api.core.exceptions.NopException(io.nop.config.ConfigErrors.ERR_CONFIG_INVALID_CONFIG_BEAN_NAME);
        });

        java.lang.reflect.Method refresh = JdbcConfigSource.class.getDeclaredMethod("refreshConfig");
        refresh.setAccessible(true);

        // 值变更 + refreshConfig 全管线：坏回调被隔离，管线不抛
        ROWS.set(new TreeMap<>(Map.of("k1", "v9")));
        assertDoesNotThrow(() -> refresh.invoke(source));
        assertEquals(1, goodCalls.get());

        source.close();
    }
}
