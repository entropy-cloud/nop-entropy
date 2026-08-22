package test.io.entropy.beans;

/**
 * 构造器循环依赖回归测试用 bean：B 的属性引用 A（属性注入环 + A 侧构造器边）。
 */
public class TestCtorCycleB {
    private TestCtorCycleA a;

    public void setA(TestCtorCycleA a) {
        this.a = a;
    }

    public TestCtorCycleA getA() {
        return a;
    }
}
