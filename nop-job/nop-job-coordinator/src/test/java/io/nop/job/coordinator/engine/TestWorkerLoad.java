package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.api.resource.ResourceVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestWorkerLoad {

    @Test
    void testLoadScoreMaxValueCapacityReturnsZero() {
        WorkerLoad load = new WorkerLoad();
        load.setCapacity(ResourceVector.MAX_VALUE);
        load.setReserved(new ResourceVector(500, 1000));
        assertEquals(0.0, load.loadScore(), 0.001,
                "MAX_VALUE capacity → loadScore 0 (no limit = no load)");
    }

    @Test
    void testLoadScoreZeroReservedReturnsZero() {
        WorkerLoad load = new WorkerLoad();
        load.setCapacity(new ResourceVector(1000, 2000));
        load.setReserved(ResourceVector.ZERO);
        assertEquals(0.0, load.loadScore(), 0.001,
                "Zero reserved → loadScore 0");
    }

    @Test
    void testLoadScoreNormalRatio() {
        WorkerLoad load = new WorkerLoad();
        load.setCapacity(new ResourceVector(1000, 2000));
        load.setReserved(new ResourceVector(500, 200));
        // max(500/1000, 200/2000) = max(0.5, 0.1) = 0.5
        assertEquals(0.5, load.loadScore(), 0.001);
    }

    @Test
    void testAvailable() {
        WorkerLoad load = new WorkerLoad();
        load.setCapacity(new ResourceVector(1000, 2000));
        load.setReserved(new ResourceVector(300, 500));
        ResourceVector avail = load.getAvailable();
        assertEquals(700, avail.getCpu());
        assertEquals(1500, avail.getMemory());
    }

    @Test
    void testAvailableNullReserved() {
        WorkerLoad load = new WorkerLoad();
        load.setCapacity(new ResourceVector(1000, 2000));
        assertEquals(1000, load.getAvailable().getCpu());
        assertEquals(2000, load.getAvailable().getMemory());
    }

    // ========== AR-90: dispatcher 侧 parseCapacity "0"/负数语义（与 worker 侧一致）==========

    @Test
    void testDispatcherParseCapacityLiteralZeroMeansMaxValue() {
        assertEquals(Integer.MAX_VALUE, DefaultWorkerLoadProvider.parseCapacity("0"),
                "dispatcher side: metadata '0' must mean unset→MAX_VALUE, not a real-zero black hole (AR-90)");
        assertEquals(Integer.MAX_VALUE, DefaultWorkerLoadProvider.parseCapacity(null));
        assertEquals(Integer.MAX_VALUE, DefaultWorkerLoadProvider.parseCapacity("  "));
        assertEquals(4000, DefaultWorkerLoadProvider.parseCapacity("4000"));
    }

    @Test
    void testDispatcherParseCapacityNegativeThrows() {
        assertThrows(NopException.class, () -> DefaultWorkerLoadProvider.parseCapacity("-1"),
                "dispatcher side: negative capacity must throw (AR-90), not silently disable the worker");
    }
}
