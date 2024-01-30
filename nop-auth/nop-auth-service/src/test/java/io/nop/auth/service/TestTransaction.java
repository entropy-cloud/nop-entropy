package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.auth.service.biz.TestService;
import io.nop.autotest.junit.JunitAutoTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled
@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestTransaction extends JunitAutoTestCase {
    @Inject
    TestService testService;

    @EnableSnapshot
    @Test
    public void testRequiresNew() {
        try {
            testService.methodA();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e instanceof IllegalStateException);
        }
    }
}
