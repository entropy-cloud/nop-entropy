package io.nop.cluster.elector;

import io.nop.api.core.annotations.ioc.InjectValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.4-C1（plan 2395）：选主租约安全间隔的配置绑定注解前缀必须是 IoC 认知的
 * "@cfg:"——"@cfg;" 笔误使装配即抛 ERR_IOC_INVALID_BIND_EXPR（启动失败）。
 * 反射断言钉定注解文本，防笔误回归。
 */
public class TestLeaderElectorConfigBinding {

    @Test
    public void testLeaseSafeGapBindingUsesRecognizedCfgPrefix() throws Exception {
        Method setter = AbstractLeaderElector.class.getMethod("setLeaseSafeGap", int.class);
        InjectValue injectValue = setter.getAnnotation(InjectValue.class);
        assertNotNull(injectValue, "setLeaseSafeGap must carry @InjectValue");

        String expr = injectValue.value();
        assertTrue(expr.startsWith("@cfg:"),
                "binding expr must use the IoC-recognized '@cfg:' prefix, got: " + expr);
        assertTrue(!expr.contains("@cfg;"), "the '@cfg;' typo must not reappear");
        assertEquals("@cfg:nop.cluster.leader.lease-safe-gap|4000", expr);
    }
}
