package io.nop.duckdb;

import io.nop.task.ITaskStepRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-01（plan 2502 Phase 6/分片 E）close 桥接回归：双载体账本（静态 OPEN_FILES 注册表 +
 * 实例 connections）随连接生命周期闭合。
 *
 * <p>覆盖面：
 * <ul>
 * <li>① step 消费链执行后 OPEN_FILES 条目随 refs 归零释放（载体 A）</li>
 * <li>② connections 账本随包装 close 清理（载体 B，C6-R2-001 面）</li>
 * <li>③ rule 22 端到端：step 消费链执行 → refs 归零 → 跨 engine（异配置）重开同路径
 * 不再误拒 ERR_DUCKDB_CONFIG_CONFLICT（registry 自述场景 100% 假阳性 bizFatal 的回归钉）</li>
 * <li>④ engine.close() 兜底幂等断言（既有 close 语义零回退）</li>
 * </ul>
 * 设计差异条款（勘误 7）：本工单为进程内生命周期配对形态，DB 保留期参数不适用——
 * 见 DuckDbEngine 类注释与设计 SSOT ledger-retention-policy.md §3.5 ①。
 */
public class TestDuckDbLedgerCloseBridge_W_01 {

    private static String normalized(String path) {
        return new File(path).getAbsoluteFile().getPath();
    }

    /**
     * 最小 step 运行时桩：仅解析 step-local inputs（getLocalValue），其余方法不参与
     * DuckDbSqlTaskStep.execute 的连接获取/释放链路。
     */
    private static ITaskStepRuntime stepRuntime(Map<String, Object> inputs) {
        return (ITaskStepRuntime) Proxy.newProxyInstance(
                ITaskStepRuntime.class.getClassLoader(), new Class[]{ITaskStepRuntime.class},
                (proxy, method, args) -> {
                    if ("getLocalValue".equals(method.getName()))
                        return inputs.get((String) args[0]);
                    if (method.getReturnType() == boolean.class)
                        return false;
                    return null;
                });
    }

    /**
     * ①+② 双载体：真实 DuckDbSqlTaskStep 消费链（try-with-resources 关闭连接）执行后，
     * OPEN_FILES 条目随 refs 归零释放，connections 账本同步清零——接线验证（rule 23）与
     * 载体释放断言合一：包装连接被 step 真实持有并关闭，非仅包装类型存在。
     */
    @Test
    @Timeout(60)
    public void testW_01_stepConsumptionChainReleasesBothLedgers() throws Exception {
        Path dbFile = Files.createTempDirectory("w01-both-ledgers").resolve("chain.duckdb");
        DuckDbEngine engine = new DuckDbEngine();
        engine.memoryLimit = "128MB";
        try {
            DuckDbSqlTaskStep step = new DuckDbSqlTaskStep();
            step.setEngine(engine);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("sql", "CREATE TABLE t_w01(k INTEGER)");
            inputs.put("dbPath", dbFile.toString());
            step.execute(stepRuntime(inputs));

            // 载体 A：文件注册表 refs 归零即条目移除
            assertNull(DuckDbEngine.openFileRefCount(normalized(dbFile.toString())),
                    "step 消费链 close 后 OPEN_FILES 条目必须随 refs 归零释放（载体 A，C6-001）");
            // 载体 B：connections 账本随包装 close 清理
            assertEquals(0, engine.trackedConnectionCount(),
                    "step 消费链 close 后 connections 账本必须清零（载体 B，C6-R2-001）");
        } finally {
            engine.close();
        }
    }

    /**
     * ③ rule 22 端到端：step 消费链执行 → refs 归零 → 跨 engine（异配置）重开同路径不误拒。
     * 修复前基线：TWRS 直接关原连接旁路 releaseOpen，OPEN_FILES 残留 first 引擎指纹（refs≥1），
     * second.openFile 在注册表指纹检查段 100% 假阳性 bizFatal ERR_DUCKDB_CONFIG_CONFLICT。
     */
    @Test
    @Timeout(60)
    public void testW_01_rule22_stepChainThenReopenSamePathAcrossEngines() throws Exception {
        Path dbFile = Files.createTempDirectory("w01-rule22").resolve("reopen.duckdb");
        DuckDbEngine first = new DuckDbEngine();
        first.memoryLimit = "128MB";
        try {
            DuckDbSqlTaskStep step = new DuckDbSqlTaskStep();
            step.setEngine(first);
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("sql", "CREATE TABLE t_rule22(k INTEGER)");
            inputs.put("dbPath", dbFile.toString());
            step.execute(stepRuntime(inputs));

            // 消费链执行后中间态：refs 必须已归零（端到端链第二环）
            assertNull(DuckDbEngine.openFileRefCount(normalized(dbFile.toString())),
                    "rule 22 链第二环：step close 后 refs 必须归零");
        } finally {
            first.close();
        }

        // 第三环：异配置新 engine 重开同路径——不得命中残留指纹误拒
        DuckDbEngine second = new DuckDbEngine();
        second.memoryLimit = "256MB";
        try {
            Connection conn = second.openFile(dbFile.toString());
            assertNotNull(conn, "同路径异配置重开必须成功（指纹误拒面闭合）");
            assertFalse(conn.isClosed());
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM t_rule22")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "first 引擎 step 已建表须跨引擎可见（数据持久语义不变）");
            }
            conn.close();
        } finally {
            second.close();
        }
    }

    /**
     * ④ engine.close() 兜底幂等（既有 close 语义零回退）：从不关闭包装的消费方由
     * engine.close() 关闭原连接并释放双载体账本；此后包装再 close 无害、engine 重复 close
     * 无害（幂等）。
     */
    @Test
    @Timeout(60)
    public void testW_01_engineCloseFallbackIdempotentZeroRegression() throws Exception {
        Path dbFile = Files.createTempDirectory("w01-fallback").resolve("fallback.duckdb");
        DuckDbEngine engine = new DuckDbEngine();
        try {
            Connection neverClosed = engine.openFile(dbFile.toString());
            neverClosed.createStatement().execute("CREATE TABLE t_fallback(k INTEGER)");

            engine.close();

            assertEquals(0, engine.trackedConnectionCount(),
                    "engine.close() 兜底必须清空 connections 账本（语义零回退）");
            assertNull(DuckDbEngine.openFileRefCount(normalized(dbFile.toString())),
                    "engine.close() 兜底必须释放 OPEN_FILES refs（语义零回退）");
            assertThrows(Exception.class, neverClosed::createStatement,
                    "engine.close() 后原连接必须被关闭（兜底语义不变）");

            // 幂等：包装事后 close 与 engine 重复 close 均无害
            neverClosed.close();
            engine.close();
            assertEquals(0, engine.trackedConnectionCount());
        } finally {
            engine.close();
        }
    }

    /**
     * 包装 close 幂等 + 真实关闭：重复 close 只记账一次（refs 不反复递减），底层连接真实
     * 关闭（isClosed=true）——包装不是只做簿记的空壳。
     */
    @Test
    @Timeout(60)
    public void testW_01_bridgeCloseIdempotentAndReallyCloses() throws Exception {
        Path dbFile = Files.createTempDirectory("w01-idempotent").resolve("bridge.duckdb");
        DuckDbEngine engine = new DuckDbEngine();
        try {
            Connection conn = engine.openFile(dbFile.toString());
            assertEquals(Integer.valueOf(1), DuckDbEngine.openFileRefCount(normalized(dbFile.toString())));
            assertEquals(1, engine.trackedConnectionCount());

            conn.close();
            assertTrue(conn.isClosed(), "包装 close 必须真实关闭底层连接");

            // 重复 close：记账幂等，refs 已释放不重复递减，无异常
            conn.close();
            assertNull(DuckDbEngine.openFileRefCount(normalized(dbFile.toString())));
            assertEquals(0, engine.trackedConnectionCount());
        } finally {
            engine.close();
        }
    }
}
