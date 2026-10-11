package io.nop.batch.dsl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.manager.IBatchTaskManager;
import io.nop.orm.IOrmEntity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestBatchProcessorOrderAndEqlRemoval extends JunitAutoTestCase {

    @Inject
    IBatchTaskManager batchTaskManager;

    /**
     * P1-3.3-002（plan 2381 Phase 1）：processor order 排序接线判别——
     * 声明序（order=2 在前）与 order 序（1→2）相反，执行链序必须按 order。
     */
    @Test
    public void testProcessorOrderSortingWired() {
        TestProcessorOrderCollector.reset();
        IBatchTask task = batchTaskManager.loadBatchTaskFromPath("/test/batch/processor-order.batch.xml",
                io.nop.api.core.ioc.BeanContainer.instance());
        task.execute(new BatchTaskContextImpl());

        assertEquals(Arrays.asList("p1:x", "p2:x", "consumed:x"), TestProcessorOrderCollector.items(),
                "processors must execute in order=1,2 sequence regardless of declaration order");
    }
}
