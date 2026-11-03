package io.nop.duckdb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-02（plan 2529 Phase 2 组 A / 设计裁定①）open/close 竞态世代守卫回归钉。
 *
 * <p>交错构造（裁定①.5，零 sleep 零轮询的闩结构）：T1 经 openFile/openMemory 入口通过
 * {@code checkClosed()} 后停在 {@code newJdbcConnection} 连接慢区（BrokenNativeEngine
 * :197-199 缝先例）；主线程 {@code close()} 置 {@code closed=true} 并清空快照；放行 T1 走完
 * connect/applySettings/registerOpen 进入 {@code track}——世代守卫必须在 track 锁内命中：
 * 先关闭原连接、归还 OPEN_FILES refs，再显式抛 ERR_DUCKDB_ENGINE_CLOSED（bizFatal）。
 * 否则迟到连接被 track 进已关闭引擎（close 幂等早退永不再关）→ native 句柄 + 单写者
 * 文件锁泄漏，{@code IDuckDbEngine} :16「close() releases every connection」契约违约。
 *
 * <p>双入口同测（openFile / openMemory）；锁无残留断言以 bare {@code DriverManager}
 * 立即重开同文件承载；二次 close 幂等断言；W-01 钉零回退面（观察钩子
 * {@code trackedConnectionCount}/{@code openFileRefCount} 复用——守卫拒绝路径连接从未入账）。
 */
public class TestDuckDbEngineCloseRace_W_02 {

    /**
     * Inject a deterministic stop in the connect slow zone: T1 has already passed
     * checkClosed() and parks before the driver touch; the test closes the engine while
     * T1 is parked, then releases it into applySettings/registerOpen/track.
     */
    static final class LatchEngine extends DuckDbEngine {
        final CountDownLatch connectStarted = new CountDownLatch(1);
        final CountDownLatch releaseGate = new CountDownLatch(1);

        @Override
        protected Connection newJdbcConnection(String url) throws SQLException {
            connectStarted.countDown();
            try {
                releaseGate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("interrupted while waiting for test gate");
            }
            return super.newJdbcConnection(url);
        }
    }

    /**
     * openFile 入口：迟到连接必须先关闭后拒绝（先关后拒次序，裁定①.1）。
     */
    @Test
    @Timeout(60)
    public void testW_02_openFileLateConnectionClosedThenRejected() throws Exception {
        Path dbFile = Files.createTempDirectory("w02-openfile").resolve("race.duckdb");
        String normalized = new File(dbFile.toString()).getAbsoluteFile().getPath();
        LatchEngine engine = new LatchEngine();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Connection> handedOut = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                handedOut.set(engine.openFile(dbFile.toString()));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "w02-t1-openFile");
        t1.start();
        assertTrue(engine.connectStarted.await(30, TimeUnit.SECONDS),
                "T1 must reach the connect slow zone deterministically");

        // T1 has passed checkClosed(); close the engine while T1 is parked, then release it
        engine.close();
        engine.releaseGate.countDown();
        t1.join(30_000);
        assertFalse(t1.isAlive(), "T1 must terminate after the gate is released");

        // (a) the late connection is rejected with the explicit closed-engine error
        assertTrue(failure.get() instanceof NopDuckDbException,
                "late openFile must be rejected, got: " + failure.get());
        NopDuckDbException e = (NopDuckDbException) failure.get();
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_ENGINE_CLOSED.getErrorCode(), e.getErrorCode(),
                "guard must reject with ERR_DUCKDB_ENGINE_CLOSED, got: " + e.getErrorCode());
        assertTrue(e.isBizFatal(), "guard rejection must be bizFatal (stop the retry chain)");
        assertNull(handedOut.get(), "no connection may be handed out after close");

        // (b) close-then-reject: the underlying connection was closed before the rejection,
        //     so the single-writer file lock is not leaked - bare DriverManager can reopen
        //     the same file immediately
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbFile);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            assertTrue(rs.next(), "file lock must not be leaked by the rejected connection");
        }

        // (c) ledger stays clean: the guard-rejected connection never enters the bookkeeping
        assertEquals(0, engine.trackedConnectionCount(),
                "rejected connection must never enter the connections ledger");
        assertNull(DuckDbEngine.openFileRefCount(normalized),
                "OPEN_FILES refs registered before the guard must be released on rejection");

        // (d) close stays idempotent after the guard hit
        engine.close();
        assertEquals(0, engine.trackedConnectionCount());
    }

    /**
     * openMemory 入口：同一世代守卫封死第二入口（无文件注册表断言面）。
     */
    @Test
    @Timeout(60)
    public void testW_02_openMemoryLateConnectionClosedThenRejected() throws Exception {
        LatchEngine engine = new LatchEngine();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Connection> handedOut = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                handedOut.set(engine.openMemory());
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "w02-t1-openMemory");
        t1.start();
        assertTrue(engine.connectStarted.await(30, TimeUnit.SECONDS),
                "T1 must reach the connect slow zone deterministically");

        engine.close();
        engine.releaseGate.countDown();
        t1.join(30_000);
        assertFalse(t1.isAlive(), "T1 must terminate after the gate is released");

        assertTrue(failure.get() instanceof NopDuckDbException,
                "late openMemory must be rejected, got: " + failure.get());
        NopDuckDbException e = (NopDuckDbException) failure.get();
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_ENGINE_CLOSED.getErrorCode(), e.getErrorCode(),
                "guard must reject with ERR_DUCKDB_ENGINE_CLOSED, got: " + e.getErrorCode());
        assertTrue(e.isBizFatal(), "guard rejection must be bizFatal");
        assertNull(handedOut.get(), "no connection may be handed out after close");
        assertEquals(0, engine.trackedConnectionCount(),
                "rejected connection must never enter the connections ledger");

        engine.close();
        assertEquals(0, engine.trackedConnectionCount());
    }
}
