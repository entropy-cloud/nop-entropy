package test.io.entropy.beans;

/**
 * 构造器循环依赖回归测试用 bean：A 的构造器引用 B，B 的属性引用 A。
 */
public class TestCtorCycleA {
    public static int createdCount = 0;

    private final TestCtorCycleB b;

    public TestCtorCycleA(TestCtorCycleB b) {
        this.b = b;
        createdCount++;
    }

    public TestCtorCycleB getB() {
        return b;
    }

    public static void reset() {
        createdCount = 0;
    }
}
