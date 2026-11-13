package io.nop.tcc.dao.store;

import io.nop.api.core.time.IEstimatedClock;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.tcc.dao.entity.NopTccRecord;
import io.nop.tcc.dao.test.AbstractTccTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * plan 2543 红⑧（W-08）：tcc 期限/租约的读/写侧时钟 seam **基类实现**。
 * <p>
 * 缺陷态（改前）：全链 {@code CoreMetrics.currentTimeMillis()/currentTimestamp()} 直读本地墙钟——
 * NTP 前跳使所有 expireTime 瞬落入恢复窗口（恢复风暴），回拨使恢复冻结。
 * 修复后：读侧（过期判定/清理窗口）走 DB 估计钟 <b>min</b>（只有确定已过期才恢复），
 * 写侧（所有落库时间戳）走 <b>max</b>（保守偏后，绝不提前恢复）。
 * <p>
 * 钉法要点：<b>不覆写 seam</b>（覆写即绕开被测实现，变异不可见），而是用 stub
 * {@code IOrmEntityDao} 让 {@code getDbEstimatedClock()} 返回已知 min/max，直接断言基类取哪个。
 */
public class TestCraR410TccClockPins extends AbstractTccTest {

    /** 红⑧a：`nowForRead` 必须取 DB 估计钟的 **min**（不得退化为本地墙钟或 max）。 */
    @Test
    public void pin08a_readSeamTakesMinClock() {
        long min = 1_111_111L;
        long max = 9_999_999L;
        TccRecordStore store = newStore();

        assertEquals(min, store.nowForRead(stubOrmDao(min, max)),
                "读侧 seam 必须取 DB 估计钟 min（只有确定已过期才恢复）");
        assertNotEquals(io.nop.api.core.time.CoreMetrics.currentTimeMillis(),
                store.nowForRead(stubOrmDao(min, max)),
                "读侧 seam 不得退化为本地墙钟");
    }

    /** 红⑧b：`nowForWrite` 与 `nowTimestampForWrite` 必须取 DB 估计钟的 **max**。 */
    @Test
    public void pin08b_writeSeamTakesMaxClock() {
        long min = 1_111_111L;
        long max = 9_999_999L;
        TccRecordStore store = newStore();

        assertEquals(max, store.nowForWrite(stubOrmDao(min, max)),
                "写侧 seam 必须取 DB 估计钟 max（保守偏后）");
        assertNotEquals(io.nop.api.core.time.CoreMetrics.currentTimeMillis(),
                store.nowForWrite(stubOrmDao(min, max)),
                "写侧 seam 不得退化为本地墙钟");
    }

    // ==================== 测试载体 ====================

    private TccRecordStore newStore() {
        TccRecordStore store = new TccRecordStore();
        store.setDaoProvider(daoProvider);
        store.setDefaultBranchTimeout(10_000);
        store.setDefaultTxnTimeout(60_000);
        store.setDefaultMaxRetryTimes(100);
        return store;
    }

    /** 最小 stub：只让 getDbEstimatedClock 返回固定 min/max，其余方法返回类型零值（本测试不触及）。 */
    @SuppressWarnings("unchecked")
    private IOrmEntityDao<NopTccRecord> stubOrmDao(long min, long max) {
        IEstimatedClock clock = new IEstimatedClock() {
            @Override
            public long getMinCurrentTimeMillis() {
                return min;
            }

            @Override
            public long getMaxCurrentTimeMillis() {
                return max;
            }

            @Override
            public io.nop.api.core.beans.LongRangeBean getCurrentTimeRange() {
                return io.nop.api.core.beans.LongRangeBean.of(min, max);
            }
        };
        return (IOrmEntityDao<NopTccRecord>) Proxy.newProxyInstance(
                IOrmEntityDao.class.getClassLoader(),
                new Class[]{IOrmEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getDbEstimatedClock"))
                        return clock;
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    if (rt == int.class) return 0;
                    if (rt == long.class) return 0L;
                    return null;
                });
    }
}
