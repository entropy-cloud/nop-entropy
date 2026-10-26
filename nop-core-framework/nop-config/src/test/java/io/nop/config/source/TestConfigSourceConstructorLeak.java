package io.nop.config.source;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.source.file.AbstractFileConfigSource;
import io.nop.config.source.jdbc.JdbcConfig;
import io.nop.config.source.jdbc.JdbcConfigSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2506 2.2-C6-003 回归：配置源构造器「先调度后加载」缺陷——初始装载失败时对象半构造，
 * 已注册的周期刷新任务无任何引用可取消，永久泄漏于 JVM 静态 globalTimer（泄漏窗口覆盖全运行期
 * 并跨 destroy→reinit 代际）；jdbc 变体附带已建连接滞留与 DB 故障期周期性连接风暴。
 * <p>
 * 修复裁定⑤（设计 plugin-lifecycle-and-config-source-hardening.md §3.5）= 先加载成功再调度：
 * 构造期装载失败即 fail-fast 上抛、零残留调度；jdbc 变体失败路径关闭已建连接。
 * <p>
 * N7 禁令合规：两变体均以 refreshInterval &gt; 0 真实走 globalTimer 调度路径（等待超过一个调度
 * 周期观测「零调度」），不以 refreshInterval=0 弱形态伪装调度路径覆盖。
 */
public class TestConfigSourceConstructorLeak {

    /**
     * 构造期装载失败的 jdbc 桩：createConnection 可计数并为每条连接记录 close 次数；
     * executeQuery 注入 SQLException 使构造期 loadConfig 失败。计数器置于桩 config 内，
     * 构造抛错后测试侧仍可观测后续调度轮次的资源行为。
     */
    static class ConstructorFailJdbcConfig extends JdbcConfig {
        final AtomicInteger createdConnections = new AtomicInteger();
        final List<AtomicInteger> closeCounts = new CopyOnWriteArrayList<>();

        @Override
        public Connection createConnection() throws SQLException {
            createdConnections.incrementAndGet();
            AtomicInteger closedCount = new AtomicInteger();
            closeCounts.add(closedCount);
            return failingConnection(closedCount);
        }

        private Connection failingConnection(AtomicInteger closedCount) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("createStatement")) {
                            return failingStatement();
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

        private Statement failingStatement() {
            return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                    new Class[]{Statement.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("setQueryTimeout")) {
                            return null;
                        }
                        if (method.getName().equals("executeQuery")) {
                            throw new SQLException("nop-test: constructor-time load failure");
                        }
                        return null;
                    });
        }
    }

    /**
     * 构造期装载失败的 file 桩：装载计数器置于测试侧（静态），构造抛错后仍可观测
     * 调度轮次是否继续执行（缺陷形态 = 半构造对象被周期任务引用持有，计数持续增长）。
     */
    static final AtomicInteger FILE_LOADS = new AtomicInteger();

    static class ConstructorFailFileSource extends AbstractFileConfigSource {
        ConstructorFailFileSource(long refreshIntervalMillis) {
            super(List.of("/nonexistent-test-path"), refreshIntervalMillis);
        }

        @Override
        public String getName() {
            return "constructor-fail-probe";
        }

        @Override
        protected Map<String, ValueWithLocation> loadConfigFromPath(List<Path> paths) {
            FILE_LOADS.incrementAndGet();
            throw new IllegalStateException("nop-test: constructor-time load failure");
        }
    }

    @BeforeEach
    public void resetFileLoads() {
        FILE_LOADS.set(0);
    }

    /**
     * jdbc 变体（C10-001 成员面）：构造期 loadConfig 抛错（refreshInterval&gt;0 真实调度路径）→
     * 修复后零调度（无周期任务再建连接）+ 构造期已建连接已在失败路径关闭；
     * 修复前红：构造异常抛出后周期任务仍触发，故障期每轮重建连接（连接风暴），
     * createdConnections 计数增长，且构造期连接从未关闭。
     */
    @Test
    @Timeout(30)
    public void testJdbcConstructorFailureSchedulesNothingAndClosesCreatedConnection() throws Exception {
        ConstructorFailJdbcConfig config = new ConstructorFailJdbcConfig();
        config.setSelectAllQuery("select name, value from t");
        // N7 禁令合规：refreshInterval > 0 真实注册 globalTimer 调度
        config.setRefreshInterval(Duration.ofSeconds(1));

        // 构造 fail-fast 语义保留：初始装载失败必须上抛（NopWrapException extends NopException）
        NopException e = assertThrows(NopException.class, () -> new JdbcConfigSource(config));
        assertNotNull(e.getErrorCode());

        // 等待远超一个调度周期（1s）：修复后零调度，createConnection 计数恒为构造期那一次
        Thread.sleep(2500);

        assertEquals(1, config.createdConnections.get(),
                "构造失败后不得再有周期刷新任务建立新连接（先加载成功再调度，零残留调度）");
        assertEquals(1, config.closeCounts.get(0).get(),
                "构造期已建连接必须在构造失败路径关闭");
    }

    /**
     * file 变体（C10-008 成员面）：Scripted 桩构造期抛错（refreshInterval&gt;0 真实调度路径）→
     * 修复后零调度（装载计数恒为构造期那一次）；
     * 修复前红：构造异常抛出后周期任务仍触发，装载计数持续增长。
     */
    @Test
    @Timeout(30)
    public void testFileConstructorFailureSchedulesNothing() throws Exception {
        // N7 禁令合规：refreshInterval > 0 真实注册 globalTimer 调度（100ms 周期）
        RuntimeException e = assertThrows(IllegalStateException.class,
                () -> new ConstructorFailFileSource(100));
        assertNotNull(e.getMessage());

        // 等待远超一个调度周期（100ms）：修复后零调度，装载计数恒为构造期那一次
        Thread.sleep(1000);

        assertEquals(1, FILE_LOADS.get(),
                "构造失败后不得再有调度轮次执行装载（先加载成功再调度，零残留调度）");
    }
}
