package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, testConfigFile = "classpath:my.properties")
public class TestConfigProperties extends JunitBaseTestCase {
    static final IConfigReference<Boolean> CFG_TEST = AppConfig.varRef(null, "my.test", Boolean.class, false);

    @Test
    public void testGetProperty() {
        assertTrue(CFG_TEST.get());
    }
}
