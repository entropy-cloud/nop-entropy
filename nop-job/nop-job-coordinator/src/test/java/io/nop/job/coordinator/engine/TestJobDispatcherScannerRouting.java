package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.dao.entity.NopJobFire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link JobDispatcherScannerImpl#resolveTaskBuilder(NopJobFire)} routing logic.
 * Plan 339: dispatchMode is the single coordinator-side routing key; executorKind no longer
 * participates in coordinator routing. single/null/blank all normalize to the "single" builder;
 * unknown modes fail fast with ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED.
 */
public class TestJobDispatcherScannerRouting {

    private JobDispatcherScannerImpl dispatcher;
    private IJobTaskBuilder singleBuilder;
    private IJobTaskBuilder partitionBuilder;
    private IJobTaskBuilder broadcastBuilder;
    private IJobTaskBuilder bestFitBuilder;

    @BeforeEach
    void setUp() {
        singleBuilder = new DefaultJobTaskBuilder();
        partitionBuilder = stub();
        broadcastBuilder = stub();
        bestFitBuilder = stub();
        dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setTaskBuilders(Map.of(
                "single", singleBuilder,
                "partition", partitionBuilder,
                "broadcast", broadcastBuilder,
                "bestFit", bestFitBuilder));
    }

    private static IJobTaskBuilder stub() {
        return new IJobTaskBuilder() {
            @Override
            public List<io.nop.job.dao.entity.NopJobTask> buildTasks(NopJobFire fire) {
                return List.of();
            }
        };
    }

    private NopJobFire createFire(String dispatchMode, String executorKind) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("test-fire");
        fire.setDispatchMode(dispatchMode);
        fire.setExecutorKind(executorKind);
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of());
        return fire;
    }

    @Test
    void testDispatchModePartitionRoutesToPartitionBuilder() {
        assertEquals(partitionBuilder, dispatcher.resolveTaskBuilder(createFire("partition", "test")));
    }

    @Test
    void testDispatchModeBroadcastRoutesToBroadcastBuilder() {
        assertEquals(broadcastBuilder, dispatcher.resolveTaskBuilder(createFire("broadcast", "test")));
    }

    @Test
    void testDispatchModeBestFitRoutesToAdaptiveBuilder() {
        assertEquals(bestFitBuilder, dispatcher.resolveTaskBuilder(createFire("bestFit", "test")));
    }

    @Test
    void testDispatchModeSingleRoutesToSingleBuilder() {
        assertEquals(singleBuilder, dispatcher.resolveTaskBuilder(createFire("single", "test")));
    }

    @Test
    void testDispatchModeNullRoutesToSingleBuilder() {
        assertEquals(singleBuilder, dispatcher.resolveTaskBuilder(createFire(null, "test")));
    }

    @Test
    void testDispatchModeBlankRoutesToSingleBuilder() {
        assertEquals(singleBuilder, dispatcher.resolveTaskBuilder(createFire("  ", "test")));
    }

    /**
     * Plan 339: executorKind must NOT participate in coordinator routing. A schedule with
     * dispatchMode=single + executorKind=rpcBroadcast (previously a legal broadcast route via
     * the executorKind fallback) must now resolve to the single builder.
     */
    @Test
    void testDispatchModeSingleWithExecutorKindRpcBroadcastRoutesToSingle() {
        NopJobFire fire = createFire("single", "rpcBroadcast");
        assertEquals(singleBuilder, dispatcher.resolveTaskBuilder(fire),
                "executorKind must not affect coordinator routing (plan 339)");
    }

    /**
     * AR-87: unknown dispatchMode must fail fast with ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED
     * (config error), never silently degrade to single.
     */
    @Test
    void testUnknownDispatchModeThrowsExplicitly() {
        NopJobFire fire = createFire("typo", "test");
        NopException ex = assertThrows(NopException.class, () -> dispatcher.resolveTaskBuilder(fire),
                "unknown dispatchMode must throw, not silently fall back");
        assertEquals(ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED.getErrorCode(), ex.getErrorCode());
    }
}
