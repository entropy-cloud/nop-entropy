/**
 * 并发初始化死锁复现测试辅助类：
 * P 依赖 D（depends-on），P 的 init 在 resolvedDepends 阶段经 ctxA 强制创建 D。
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;

public class TestConcurrentP {
    private static volatile boolean initRan;
    private static volatile boolean dInitedBeforeInit;

    public static void reset() {
        initRan = false;
        dInitedBeforeInit = false;
    }

    public static boolean isInitRan() {
        return initRan;
    }

    public static boolean isDInitedBeforeInit() {
        return dInitedBeforeInit;
    }

    @PostConstruct
    public void init() {
        initRan = true;
        dInitedBeforeInit = TestConcurrentD.isInited();
    }
}