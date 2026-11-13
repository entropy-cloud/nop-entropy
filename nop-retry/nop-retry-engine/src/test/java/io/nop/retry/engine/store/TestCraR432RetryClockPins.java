package io.nop.retry.engine.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryPolicy;
import static io.nop.retry.dao._NopRetryDaoConstants.BLOCK_STRATEGY_DISCARD;
import io.nop.retry.api.NopRetryApiConstants;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.impl.RetryTaskImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;

import static io.nop.retry.api.NopRetryApiConstants.DEFAULT_MAX_RETRY_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R4-32（1.6-C7-011，plan 2566）红⑧ 形态钉（2543 TestCraR410TccClockPins 同形态）：
 * 钉不覆写 store 缝——stub {@code getDbEstimatedClock()} 下游依赖返回已知 min/max（min≠max），
 * 断言首提租约写值（newRecord 的 nextTriggerTime 起点）与诊断读值取 <strong>max 侧</strong>。
 * 变异红证 = 把 {@code getCurrentTime()} 回退本地 {@code CoreMetrics.currentTimeMillis()} →
 * 断言 expected max侧已知值 but was 本地墙钟 RED。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestCraR432RetryClockPins extends JunitAutoTestCase {

    /**
     * 已知双针 DB 估计钟：min/max 分离且与本地墙钟错开
     */
    static class KnownEstimatedClock implements IEstimatedClock {
        final long minMillis = 1_700_000_000_000L;
        final long maxMillis = 1_700_000_500_000L;

        @Override
        public long getMinCurrentTimeMillis() {
            return minMillis;
        }

        @Override
        public long getMaxCurrentTimeMillis() {
            return maxMillis;
        }

        @Override
        public io.nop.api.core.beans.LongRangeBean getCurrentTimeRange() {
            return io.nop.api.core.beans.LongRangeBean.of(minMillis, maxMillis - minMillis);
        }
    }

    /**
     * daoProvider 替身：daoFor(NopRetryRecord) 返回 getDbEstimatedClock→已知钟的 dao 代理
     * （其余方法透传真实 dao；红⑧ 纪律：钉不覆写 store 缝本身，只 stub 缝下游依赖）
     */
    @SuppressWarnings("unchecked")
    IDaoProvider stubbedDaoProvider(IDaoProvider real, KnownEstimatedClock clock) {
        return (IDaoProvider) Proxy.newProxyInstance(IDaoProvider.class.getClassLoader(),
                new Class[]{IDaoProvider.class}, (proxy, method, args) -> {
                    if ("daoFor".equals(method.getName()) && NopRetryRecord.class.equals(args[0])) {
                        Object realDao = method.invoke(real, args);
                        return Proxy.newProxyInstance(IOrmEntityDao.class.getClassLoader(),
                                new Class[]{IOrmEntityDao.class}, (p, m, a) -> {
                                    if ("getDbEstimatedClock".equals(m.getName()))
                                        return clock;
                                    try {
                                        return m.invoke(realDao, a);
                                    } catch (InvocationTargetException te) {
                                        throw te.getCause();
                                    }
                                });
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException te) {
                        throw te.getCause();
                    }
                });
    }

    IDaoProvider getDaoProvider() {
        // JunitAutoTestCase 基类 daoProvider 为 private：经自身 @Inject 字段获取
        return this.daoProviderField;
    }

    @jakarta.inject.Inject
    IDaoProvider daoProviderField;

    @Test
    void testFirstLeaseWriteTakesMaxSideOfDbEstimatedClock() {
        KnownEstimatedClock clock = new KnownEstimatedClock();
        RetryRecordStoreImpl store = new RetryRecordStoreImpl();
        store.setDaoProvider(stubbedDaoProvider(getDaoProvider(), clock));

        // loadPolicy requireEntityById：先落一条 policy（真实 dao，非替身路径）
        NopRetryPolicy policy = new NopRetryPolicy();
        policy.setSid("policy-pin");
        policy.setName("Policy pin");
        policy.setNamespaceId("default");
        policy.setGroupId("default");
        policy.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        policy.setBlockStrategy(BLOCK_STRATEGY_DISCARD);
        policy.setImmediateRetryCount(0);
        store.savePolicy(policy);

        // 缝读值 = max 侧（写侧口径）
        assertEquals(clock.maxMillis, store.getCurrentTime(),
                "getCurrentTime() must take the max side of the DB estimated clock (write-side convention)");

        // 首提租约写值（newRecord nextTriggerTime 起点）= max 侧 + 首提租约窗
        RetryTaskImpl task = new RetryTaskImpl(null, "svc-pin", "method-pin");
        task.withPolicyId("policy-pin");
        task.withIdempotentId("idem-pin");
        ApiRequest<?> request = null; // newRecord 对 null request 有守卫
        NopRetryRecord record = store.newRecord((IRetryTask) task, request);

        Timestamp nextTrigger = record.getNextTriggerTime();
        assertEquals(new Timestamp(clock.maxMillis + NopRetryApiConstants.DEFAULT_RETRYING_TIMEOUT_MS),
                nextTrigger,
                "first lease write must anchor on the max side of the DB estimated clock "
                        + "(mutant revert to local wall clock yields the wall value -> RED)");
    }
}
