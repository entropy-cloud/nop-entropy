package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, testConfigFile = "classpath:my.properties")
public class TestConfigProperties extends JunitBaseTestCase {
    static final IConfigReference<Boolean> CFG_TEST = AppConfig.varRef(null, "my.test", Boolean.class, false);

    static final IConfigReference<Set> CFG_TEST_NAMES = AppConfig.varRef(null, "my.test-names", Set.class, null);

    @Test
    public void testGetProperty() {
        assertTrue(CFG_TEST.get());
        assertTrue(CFG_TEST_NAMES.get() instanceof Set);
        assertEquals("[a, b]", CFG_TEST_NAMES.get().toString());
    }

    @Test
    public void testReset() {
        IConfigReference<String> v1 = AppConfig.varRef(null, "test.a1.b", String.class, "true");
        IConfigReference<Boolean> v2 = AppConfig.varRef(null, "test.a1.b", Boolean.class, true);
        assertEquals("true", v1.get());
        assertTrue(v2.get());

        AppConfig.getConfigProvider().assignConfigValue("test.a1.b", false);

        assertEquals("false", v1.get());
        assertTrue(!v2.get());

        AppConfig.getConfigProvider().reset();

        assertEquals("true", v1.get());
        assertTrue(v2.get());
    }
}
