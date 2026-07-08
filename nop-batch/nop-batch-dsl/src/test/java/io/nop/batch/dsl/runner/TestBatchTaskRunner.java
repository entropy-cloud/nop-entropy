package io.nop.batch.dsl.runner;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestBatchTaskRunner extends JunitAutoTestCase {

    @Inject
    IBatchTaskRunner batchTaskRunner;

    @Test
    public void testExecuteByPath() {
        String taskPath = "/test/batch/test-runner-simple.batch.xml";
        assertDoesNotThrow(() -> batchTaskRunner.execute(taskPath));
    }

    @Test
    public void testExecuteWithParams() {
        String taskPath = "/test/batch/test-runner-simple.batch.xml";
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        assertDoesNotThrow(() -> batchTaskRunner.execute(taskPath, params));
    }
}
