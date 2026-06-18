package io.nop.job.coordinator.engine;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.api.resource.ResourceVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
