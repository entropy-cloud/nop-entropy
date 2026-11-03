package io.nop.duckdb;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-03（plan 2529 Phase 2 组 A / 设计裁定②）materialize CTAS table-exists 守卫回归钉。
 *
 * <p>守卫语义（裁定②.3）：resultTable 由唯一一次成功执行创建；materialize 启动时同名表已
 * 存在即为孤儿或陈旧工作集——显式拒绝（ERR_DUCKDB_TABLE_EXISTS + bizFatal，与 ingest
 * 守卫 readExternal :127-131 完全同口径），要求调用方显式清理；禁静默重建（方案 (b)
 * DROP IF EXISTS 已否决）。
 *
 * <p>红②a：持久 dbPath 预置同名 resultTable → 执行步 → 断言错误码 + bizFatal。
 * 红②b（重放回归，断言写死——评审 Minor-4）：首执行 CTAS 已提交后注入非 bizFatal 可恢复
 * 异常 → {@code <retry>} 第 2 次重放命中守卫 → ERR_DUCKDB_TABLE_EXISTS + bizFatal →
 * 任务终态 FAILED、零后续重试、孤儿表留存待显式清理（NopDuckDbException 为 RuntimeException
 * 穿透 execute 的 catch(SQLException) 不被包装为 IO_FAILED）。
 *
 * <p>W-10 同批条款（裁定⑦）：非幂等 caller-SQL 在 kill/重放窗口的必败面由类/方法 javadoc
 * 重放幂等契约声明承载，本测试类不承载声明断言。
 */
public class TestDuckDbCtasGuard_W_03 extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 最小 step 运行时桩（TestDuckDbLedgerCloseBridge_W_01 先例）：仅解析 step-local inputs。
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
     * 红②a：持久 dbPath 上预置同名 resultTable（孤儿/陈旧工作集形态）→ materialize 守卫
     * 必须 fail-fast 拒绝（错误码 + bizFatal），而非 CTAS 撞表的 io-failed 可重试形态。
     */
    @Test
    @Timeout(120)
    public void testW_03_ctasPreexistingTableRejectedBizFatal() throws Exception {
        Path dbFile = Files.createTempDirectory("w03-guard").resolve("guard.duckdb");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbFile);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t_guard(k INTEGER)");
        }

        DuckDbEngine engine = new DuckDbEngine();
        try {
            DuckDbSqlTaskStep step = new DuckDbSqlTaskStep();
            step.setEngine(engine);
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("sql", "SELECT 41 AS v");
            inputs.put("dbPath", dbFile.toString());
            inputs.put("resultTable", "t_guard");

            NopDuckDbException e = assertThrows(NopDuckDbException.class,
                    () -> step.execute(stepRuntime(inputs)));
            assertEquals(NopDuckDbErrors.ERR_DUCKDB_TABLE_EXISTS.getErrorCode(), e.getErrorCode(),
                    "guard must reject with ERR_DUCKDB_TABLE_EXISTS, got: " + e.getErrorCode()
                            + " (" + e.getMessage() + ")");
            assertTrue(e.isBizFatal(), "guard rejection must be bizFatal - retry cannot fix an orphan table");
        } finally {
            engine.close();
        }
    }

    /**
     * 红②b 重放回归：首执行 CTAS 已提交 → 注入非 bizFatal 可恢复异常 → {@code <retry>}
     * 第 2 次重放命中守卫。断言写死（评审 Minor-4）：错误码 TABLE_EXISTS（非 IO_FAILED
     * 掩盖）、bizFatal（零后续重试——RetryPolicy.isRecoverableException 对 bizFatal 短路）、
     * 执行次数恰 2、孤儿表留存待显式清理。
     */
    @Test
    @Timeout(120)
    public void testW_03_replayHitsGuardBizFatalStopsRetryChain() throws Exception {
        Path dbFile = Files.createTempDirectory("w03-replay").resolve("replay.duckdb");
        TestCtasFlakyStep.ATTEMPTS.set(0);

        ITaskFlowManager manager = new TaskFlowManagerImpl();
        ITask task = manager.loadTaskFromPath("/nop/duckdb/task/ctas-retry.task.xml");
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);
        taskRt.setInput("dbPath", dbFile.toString());
        taskRt.setInput("resultTable", "t_orphan");

        NopDuckDbException e = assertThrows(NopDuckDbException.class, () -> {
            // retryDelay=0 keeps the whole retry chain synchronous in the calling thread
            // (no scheduled attempts), so the final failure propagates directly
            io.nop.task.TaskStepReturn ret = task.execute(taskRt);
            if (ret.isAsync()) {
                ret.sync();
            }
        });

        // (i) the guard error reaches the task boundary with its own code, not masked as io-failed
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_TABLE_EXISTS.getErrorCode(), e.getErrorCode(),
                "replay must hit the table-exists guard, got: " + e.getErrorCode()
                        + " (" + e.getMessage() + ")");
        assertTrue(e.isBizFatal(), "guard rejection must be bizFatal");

        // (ii) zero further retries: first execution committed the CTAS, second execution hit
        //      the guard - the bizFatal classification short-circuits the retry chain
        assertEquals(2, TestCtasFlakyStep.ATTEMPTS.get(),
                "bizFatal guard rejection must stop the retry chain at the 2nd execution, attempts="
                        + TestCtasFlakyStep.ATTEMPTS.get());

        // (iii) the orphan table persists, explicitly awaiting cleanup (no silent rebuild)
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbFile);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM t_orphan")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "orphan table from the first execution must persist");
        }
    }
}
