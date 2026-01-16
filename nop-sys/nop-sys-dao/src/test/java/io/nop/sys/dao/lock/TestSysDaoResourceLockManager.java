package io.nop.sys.dao.lock;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.concurrent.lock.IResourceLockManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysDaoResourceLockManager extends JunitBaseTestCase {

    @Inject
    IResourceLockManager lockManager;

    @Test
    public void testLock() {
        lockManager.runWithLock("test", "aa", "DEMO", lock -> {
            assertTrue(lock.isHoldingLock());
            System.out.println("run");
        });
    }
}
