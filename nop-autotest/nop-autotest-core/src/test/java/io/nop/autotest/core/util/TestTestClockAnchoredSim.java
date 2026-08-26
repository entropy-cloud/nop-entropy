package io.nop.autotest.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锚定仿真毫秒线语义 Proof（2026-08-25 上游化，源自 nop-app-erp
 * ThreadLocalFrozenClock 升级的平台侧能力迁移，bug 参见该仓
 * docs/bugs/2026-08-25-frozen-clock-millis-testclock-displacement.md）。
 */
public class TestTestClockAnchoredSim {

    private static final long ANCHOR_MILLIS =
            LocalDate.of(2026, 7, 17).atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();

    @AfterEach
    void cleanup() {
        TestClock.clearAnchor();
    }

    @Test
    public void anchoredLineCarriesFrozenEpochAndAdvances() {
        TestClock clock = new TestClock();
        TestClock.installAnchor(ANCHOR_MILLIS);

        long sim = clock.currentTimeMillis();
        assertEquals("2026-07-17",
                new Timestamp(sim).toLocalDateTime().toLocalDate().toString(),
                "锚定后毫秒线应携带冻结纪元");
        assertTrue(clock.currentTimeMillis() >= sim, "仿真线随真实时间前进");
        assertTrue(TestClock.isAnchorActive());
    }

    @Test
    public void strictlyMonotonicNeverRepeatsUnderAnchor() {
        TestClock clock = new TestClock();
        TestClock.installAnchor(ANCHOR_MILLIS);

        long prev = clock.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            long now = clock.currentTimeMillis();
            assertTrue(now > prev, "必须严格递增（iteration " + i + "）");
            prev = now;
        }
    }

    @Test
    public void datesDeriveFromSameMillisLine() {
        TestClock clock = new TestClock();
        TestClock.installAnchor(ANCHOR_MILLIS);

        LocalDate viaDate = clock.currentDate();
        LocalDate viaDateTime = clock.currentDateTime().toLocalDate();
        LocalDate viaMillis = new Timestamp(clock.currentTimeMillis()).toLocalDateTime().toLocalDate();

        assertEquals(viaMillis, viaDate, "currentDate 与毫秒线同源");
        assertEquals(viaMillis, viaDateTime, "currentDateTime 与毫秒线同源");
        assertEquals(LocalDate.of(2026, 7, 17), viaMillis, "锚定激活：派生日期应为冻结日而非真实墙钟日");
    }

    /**
     * 暖实例不回拨契约 pinning（审查 B M1）：裸跑使 lastTime 领先（真实今日）后再装过去锚点，
     * 返回值自旧高水位 lastTime++ 继续——观察不到冻结日期，且保持严格单调。
     */
    @Test
    public void warmInstanceDoesNotRewindToPastAnchor() {
        TestClock clock = new TestClock();
        long warm = clock.currentTimeMillis(); // 裸线：真实今日量级

        TestClock.installAnchor(ANCHOR_MILLIS);
        long after = clock.currentTimeMillis();

        assertTrue(after > warm, "暖实例装过去锚点后仍须严格递增");
        assertTrue(after > ANCHOR_MILLIS,
                "高水位领先于过去锚线：返回值停留在旧水位线，不回拨到 " + new Timestamp(ANCHOR_MILLIS));
        // 语义断言：派生日期不再是冻结日（锚线被高水位遮蔽）
        assertTrue(!LocalDate.of(2026, 7, 17).equals(
                        new Timestamp(clock.currentTimeMillis()).toLocalDateTime().toLocalDate()),
                "暖实例期间不应观察到冻结日期（lastTime++ regime 直至墙钟追平）");
    }

    @Test
    public void clearAnchorRestoresRawWallClock() {
        TestClock clock = new TestClock();
        TestClock.installAnchor(ANCHOR_MILLIS);
        long frozen = clock.currentTimeMillis();

        TestClock.clearAnchor();
        long realNow = System.currentTimeMillis();
        assertFalse(TestClock.isAnchorActive());
        assertTrue(Math.abs(clock.currentTimeMillis() - realNow) < 5_000,
                "清除后回到系统真实时钟附近");
        assertTrue(frozen < realNow - 86_400_000L, "冻结线显著早于真实当前时间");
    }
}
