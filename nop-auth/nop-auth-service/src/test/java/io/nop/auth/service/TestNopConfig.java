package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true)
public class TestNopConfig extends JunitBaseTestCase {
    @Test
    public void testConfig() {
        assertTrue(AppConfig.var("app.test-config", false));
    }
}
