package io.nop.autotest.junit;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 不支持快照录制机制
 */
@ExtendWith({NopJunitExtension.class, NopJunitParameterResolver.class})
public class JunitBaseTestCase extends BaseTestCase {

    @BeforeEach
    public void init(TestInfo testInfo) {
        initBeans();
    }

    protected void initBeans() {
        if (!BeanContainer.isInitialized())
            return;

        IBeanContainer container = BeanContainer.instance();
        container.restart();

        if (!container.supportInjectTo())
            return;

        container.injectTo(this);
    }
}