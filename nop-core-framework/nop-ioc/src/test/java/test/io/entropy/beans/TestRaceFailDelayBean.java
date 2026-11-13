package test.io.entropy.beans;

/**
 * pin1b（plan 2549 C4-009）代际竞态注入 bean：delay-method 按 failNextDelay 标志决定抛错，
 * 短暂等待给 restart 让出竞态窗口（确定性：restart 先于旧代 delay 执行完成）。
 */
public class TestRaceFailDelayBean {
    /**
     * 调用计数：第 1 次（旧代）delay 抛错注入失败；第 2 次（新代）成功——无时序依赖的确定性注入。
     */
    public static final java.util.concurrent.atomic.AtomicInteger delayCalls = new java.util.concurrent.atomic.AtomicInteger();

    public static void reset() {
        delayCalls.set(0);
    }

    public void delayMe() {
        if (delayCalls.incrementAndGet() == 1)
            throw new IllegalStateException("simulated-stale-generation-failure");
    }
}
