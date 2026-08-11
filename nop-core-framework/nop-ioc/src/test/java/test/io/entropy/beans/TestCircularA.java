/**
 * 临时最小复现（TEMP-DIAG，验证后转为平台回归测试或删除）：
 * 递归环中 resolvedDepends 强制创建（getBean(name, false)）是否返回未完成 init 的 bean。
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;

public class TestCircularA {
    public boolean inited;

    public static boolean s_inited;

    private TestCircularB b;

    public void setB(TestCircularB b) {
        this.b = b;
    }

    public TestCircularB getB() {
        return b;
    }

    @PostConstruct
    public void init() {
        inited = true;
        s_inited = true;
    }
}
