package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.job.dao.entity.NopJobFire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JobDispatcherScannerImpl#resolveTaskBuilder(NopJobFire)} routing logic.
 * Verifies dispatchMode priority over executorKind, bestFit exception, and fallback behavior.
 */
public class TestJobDispatcherScannerRouting {

    private JobDispatcherScannerImpl dispatcher;
    private IJobTaskBuilder originalDefault;
    private IBeanContainer originalContainer;

    private static final IJobTaskBuilder STUB_PARTITION = new IJobTaskBuilder() {
        @Override public List<io.nop.job.dao.entity.NopJobTask> buildTasks(NopJobFire fire) { return List.of(); }
    };
    private static final IJobTaskBuilder STUB_BROADCAST = new IJobTaskBuilder() {
        @Override public List<io.nop.job.dao.entity.NopJobTask> buildTasks(NopJobFire fire) { return List.of(); }
    };

    @BeforeEach
    void setUp() {
        originalContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobTaskBuilder_partition", STUB_PARTITION);
        container.registerBean("nopJobTaskBuilder_broadcast", STUB_BROADCAST);
        BeanContainer.registerInstance(container);

        dispatcher = new JobDispatcherScannerImpl();
        IJobTaskBuilder defaultBuilder = new DefaultJobTaskBuilder();
        dispatcher.setDefaultTaskBuilder(defaultBuilder);
        originalDefault = defaultBuilder;
    }

    @AfterEach
    void tearDown() {
        if (originalContainer != null) {
            BeanContainer.registerInstance(originalContainer);
        }
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
        NopJobFire fire = createFire("partition", "test");
        IJobTaskBuilder builder = dispatcher.resolveTaskBuilder(fire);
        assertEquals(STUB_PARTITION, builder, "dispatchMode=partition should route to PartitionTaskBuilder");
    }

    @Test
    void testDispatchModeBroadcastRoutesToBroadcastBuilder() {
        NopJobFire fire = createFire("broadcast", "test");
        IJobTaskBuilder builder = dispatcher.resolveTaskBuilder(fire);
        assertEquals(STUB_BROADCAST, builder, "dispatchMode=broadcast should route to RpcBroadcastTaskBuilder");
    }

    @Test
    void testDispatchModeSingleFallsBackToExecutorKind() {
        NopJobFire fire = createFire("single", "test");
        IJobTaskBuilder builder = dispatcher.resolveTaskBuilder(fire);
        assertEquals(originalDefault, builder, "dispatchMode=single should fall back to executorKind, then default");
    }

    @Test
    void testDispatchModeNullFallsBackToExecutorKind() {
        NopJobFire fire = createFire(null, "test");
        IJobTaskBuilder builder = dispatcher.resolveTaskBuilder(fire);
        assertEquals(originalDefault, builder, "null dispatchMode should fall back to executorKind routing");
    }

    @Test
    void testDispatchModeBestFitThrowsException() {
        NopJobFire fire = createFire("bestFit", "test");
        NopException ex = assertThrows(NopException.class, () -> dispatcher.resolveTaskBuilder(fire));
        assertTrue(ex.getMessage().contains("bestFit") || ex.getErrorCode() != null,
                "bestFit should throw NopException before Plan 215");
    }
}
