/**
 * 临时最小复现（TEMP-DIAG，验证后转为平台回归测试或删除）。
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestCircularB {
    static final Logger LOG = LoggerFactory.getLogger(TestCircularB.class);

    public boolean aFullyInited;

    private TestCircularA a;

    public void setA(TestCircularA a) {
        this.a = a;
    }

    public TestCircularA getA() {
        return a;
    }

    @PostConstruct
    public void init() {
        aFullyInited = TestCircularA.s_inited;
        LOG.info("nop.ioc.diag.circular-init:beanB.init sees a.inited={}", a == null ? null : a.inited);
    }
}
