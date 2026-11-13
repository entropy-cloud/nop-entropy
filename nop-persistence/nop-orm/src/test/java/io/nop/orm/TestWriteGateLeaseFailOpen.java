/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import static io.nop.orm.OrmConfigs.CFG_ORM_MIGRATION_WRITE_GATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2507 裁定⑤（2.5-C7-004）：写门租约 fail-open——关门带时间盒，超时自动开门 + WARN 留痕，
 * 消除「开门仅本 JVM 生效时集群其余实例写冻结不可自愈（滚动重启反向加固）」。
 *
 * <p>锚定 = 本 JVM 观察到门进入 closed 态即起租（首见时间戳），不论经本地 setClosed 还是经配置源
 * 刷新观察到 closed（draft review B1：第二实例的 closed 态只来自配置源刷新）。租约键
 * {@code nop.orm.migration.write-gate-lease-seconds} 默认 1800s，≤0 = 关闭租约档（现行永久语义）。
 * 到期首次判定 WARN 一次（不刷屏），时钟经包私有注入缝 {@code setLeaseClock} 可测。</p>
 *
 * <p>既有 5 用例（TestOrmMigrationWriteGate）零回归：默认 1800s 租约远大于测试时长。</p>
 */
public class TestWriteGateLeaseFailOpen extends AbstractOrmTestCase {

    static final String LEASE_KEY = "nop.orm.migration.write-gate-lease-seconds";
    static final String LEASE_WARN_MARKER = "nop.orm.migration.write-gate-lease-expired";

    @Override
    protected void prepareData() {
        // 本测试自建数据（实际仅操作写门与配置面，无业务表依赖）
    }

    @AfterEach
    void resetWriteGateState() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_MIGRATION_WRITE_GATE,
                OrmMigrationWriteGate.GATE_OPEN);
        setLeaseSeconds(null);
        injectLeaseClock(null);
    }

    /**
     * 腿①（C10-014 时钟注入形态）：注入确定性时钟——首见 closed 起租，租约窗口内保持关闭，
     * 到期 fail-open（isClosed=false、DML 检查放行）且 WARN 恰好一次（重复观察不刷屏）。
     * 修复前红形态：setLeaseClock 注入缝不存在（无租约语义）。
     */
    @Test
    @Timeout(30)
    void testLeaseExpiryFailsOpenWithInjectedClockAndWarnsOnce() {
        AtomicLong now = new AtomicLong(1_000_000L);
        injectLeaseClock(now::get);
        try {
            setLeaseSeconds(1);
            Logger log = (Logger) LoggerFactory.getLogger(OrmMigrationWriteGate.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            log.addAppender(appender);
            try {
                OrmMigrationWriteGate.setClosed(true);
                assertTrue(OrmMigrationWriteGate.isClosed(), "gate closed within lease window");

                now.addAndGet(999);
                assertTrue(OrmMigrationWriteGate.isClosed(), "gate still closed just before lease expiry");

                now.addAndGet(1);
                assertFalse(OrmMigrationWriteGate.isClosed(), "lease expiry must fail open");
                assertDoesNotThrow(
                        () -> OrmMigrationWriteGate.checkWriteAllowed("io.nop.app.SimsCollege", "save"),
                        "fail-open must allow DML checkpoints again");

                assertEquals(1, countLeaseWarnEvents(appender), "lease expiry must WARN exactly once");
                assertFalse(OrmMigrationWriteGate.isClosed(), "fail-open persists while config stays closed");
                assertEquals(1, countLeaseWarnEvents(appender), "expiry WARN must not repeat (no log flooding)");
            } finally {
                log.detachAppender(appender);
            }
        } finally {
            injectLeaseClock(null);
            setLeaseSeconds(null);
        }
    }

    /**
     * 腿②（第二实例形态，draft review B1 定夺的可测形态）：不调 setClosed——配置源刷新将
     * closed 传播到本 JVM（updateConfigValue 直写模拟），首见观察即起租，真实时钟 1s 租约窗口
     * 内必须 fail-open。修复前红形态：无租约语义，第二实例 closed 永不自愈（有界窗内不放开）。
     */
    @Test
    @Timeout(60)
    void testSecondInstanceObservesClosedViaConfigRefreshThenLeaseFailsOpen() {
        try {
            setLeaseSeconds(1);
            AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_MIGRATION_WRITE_GATE,
                    OrmMigrationWriteGate.GATE_CLOSED);
            assertTrue(OrmMigrationWriteGate.isClosed(), "closed state observed via config source");
            assertTrue(OrmMigrationWriteGate.isClosed(), "stays closed within lease window after first sight");
            awaitGateOpen(5_000L,
                    "second-instance closed state must fail open after lease expiry (first-sight anchoring)");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_MIGRATION_WRITE_GATE,
                    OrmMigrationWriteGate.GATE_OPEN);
        }
    }

    /**
     * 腿③：租约键 ≤0 = 关闭租约档——现行永久关门语义（单机部署/确定性测试缝），越过任何正租约
     * 窗口后门仍关闭。该腿为负向对照（修复前后均绿）。
     */
    @Test
    @Timeout(60)
    void testNonPositiveLeaseDisablesLeaseKeepsGateClosedPermanently() {
        try {
            setLeaseSeconds(0);
            OrmMigrationWriteGate.setClosed(true);
            assertTrue(OrmMigrationWriteGate.isClosed(), "lease disabled (<=0) anchors nothing");
            sleepMillis(1200);
            assertTrue(OrmMigrationWriteGate.isClosed(),
                    "lease disabled (<=0) must keep permanent closed semantics");

            setLeaseSeconds(-1);
            assertTrue(OrmMigrationWriteGate.isClosed(), "negative lease keeps gate closed");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    /**
     * 腿④：配置 true→false 清零首见锚点与留痕标志——再次 closed 重新起租（新租约全窗有效，
     * 而非沿用旧锚点立即 fail-open）。修复前红形态：无租约语义，第一段到期 fail-open 不成立。
     */
    @Test
    @Timeout(60)
    void testGateReopenResetsLeaseAnchorAndReanchorsOnNextClose() {
        try {
            setLeaseSeconds(1);
            // 第一段租约：真实时钟 1s 内到期 fail-open
            OrmMigrationWriteGate.setClosed(true);
            assertTrue(OrmMigrationWriteGate.isClosed(), "first close anchors a fresh lease");
            awaitGateOpen(5_000L, "first lease must expire");

            // true→false 清零后重新 closed：重新起租
            OrmMigrationWriteGate.setClosed(false);
            OrmMigrationWriteGate.setClosed(true);
            assertTrue(OrmMigrationWriteGate.isClosed(), "fresh close must re-anchor a fresh lease");
            awaitGateOpen(5_000L, "fresh lease must expire again after re-anchor");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private static void setLeaseSeconds(Integer seconds) {
        // 经同名合成引用直写 provider（updateConfigValue 按 key 定位 canonical 实例，
        // 与产品侧 OrmConfigs 常量同一读面；不引用修复新增常量，保持缺陷形态可编译）
        IConfigReference<Integer> ref = AppConfig.varRef(
                SourceLocation.fromClass(TestWriteGateLeaseFailOpen.class), LEASE_KEY, Integer.class, 1800);
        AppConfig.getConfigProvider().updateConfigValue(ref, seconds);
    }

    /**
     * 租约时钟注入缝（包私有 {@code setLeaseClock(LongSupplier)}）经反射调用：
     * 缺失即显式失败（修复前红形态之一），不静默跳过。
     */
    private static void injectLeaseClock(LongSupplier clock) {
        try {
            Method seam = OrmMigrationWriteGate.class.getDeclaredMethod("setLeaseClock", LongSupplier.class);
            seam.setAccessible(true);
            seam.invoke(null, clock);
        } catch (Exception e) {
            throw new AssertionError("write-gate lease clock seam not available: "
                    + "OrmMigrationWriteGate.setLeaseClock(LongSupplier)", e);
        }
    }

    private static long countLeaseWarnEvents(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(e -> String.valueOf(e.getFormattedMessage()).contains(LEASE_WARN_MARKER))
                .count();
    }

    /** 有界轮询观察窗（裁定⑦：显式 await，禁无限等待；超时判红）。 */
    private static void awaitGateOpen(long timeoutMillis, String message) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        try {
            while (System.currentTimeMillis() < deadline) {
                if (!OrmMigrationWriteGate.isClosed())
                    return;
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("await interrupted: " + message, e);
        }
        fail(message + " (not satisfied within " + timeoutMillis + "ms bounded window)");
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 恢复中断位后按当前状态继续断言（观察窗内中断不吞不抛）
            Thread.currentThread().interrupt();
        }
    }
}
