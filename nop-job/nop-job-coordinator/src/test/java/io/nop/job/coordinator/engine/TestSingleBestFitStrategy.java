package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.dao.store.WorkerReservedCost;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestSingleBestFitStrategy {

    private WorkerLoad load(String instanceId, int capCpu, int capMem, int resCpu, int resMem) {
        WorkerLoad l = new WorkerLoad();
        ServiceInstance inst = new ServiceInstance();
        inst.setInstanceId(instanceId);
        l.setInstance(inst);
        l.setCapacity(new ResourceVector(capCpu, capMem));
        l.setReserved(new ResourceVector(resCpu, resMem));
        return l;
    }

    @Test
    void testSelectsLowestLoadScore() {
        List<WorkerLoad> workers = List.of(
                load("w1", 4000, 8000, 3200, 4000), // loadScore = max(0.8, 0.5) = 0.8
                load("w2", 4000, 8000, 800, 1600),  // loadScore = max(0.2, 0.2) = 0.2
                load("w3", 4000, 8000, 2000, 6400)  // loadScore = max(0.5, 0.8) = 0.8
        );
        SingleBestFitStrategy strategy = new SingleBestFitStrategy();
        AssignmentPlan plan = strategy.assign(new ResourceVector(500, 500), workers);
        assertFalse(plan.isEmpty());
        assertEquals("w2", plan.getAssignments().get(0).getWorkerInstanceId(),
                "Should select w2 (lowest loadScore 0.2)");
    }

    @Test
    void testFiltersUnfittingWorkers() {
        List<WorkerLoad> workers = List.of(
                load("w1", 1000, 2000, 900, 1900), // available = {100, 100}, task needs {500,500} → NOT fit
                load("w2", 4000, 8000, 2000, 4000) // available = {2000, 4000} → fits
        );
        SingleBestFitStrategy strategy = new SingleBestFitStrategy();
        AssignmentPlan plan = strategy.assign(new ResourceVector(500, 500), workers);
        assertFalse(plan.isEmpty());
        assertEquals("w2", plan.getAssignments().get(0).getWorkerInstanceId());
    }

    @Test
    void testReturnsEmptyWhenNoneFit() {
        List<WorkerLoad> workers = List.of(
                load("w1", 100, 200, 50, 100),   // available = {50, 100}
                load("w2", 200, 400, 100, 200)   // available = {100, 200}
        );
        SingleBestFitStrategy strategy = new SingleBestFitStrategy();
        AssignmentPlan plan = strategy.assign(new ResourceVector(500, 500), workers);
        assertTrue(plan.isEmpty(), "No worker can fit → empty plan");
    }

    @Test
    void testTiebreakerByInstanceId() {
        List<WorkerLoad> workers = List.of(
                load("zzz", 4000, 8000, 1000, 1000), // loadScore = 0.25
                load("aaa", 4000, 8000, 1000, 1000)  // loadScore = 0.25 (tie)
        );
        SingleBestFitStrategy strategy = new SingleBestFitStrategy();
        AssignmentPlan plan = strategy.assign(new ResourceVector(100, 100), workers);
        assertFalse(plan.isEmpty());
        assertEquals("aaa", plan.getAssignments().get(0).getWorkerInstanceId(),
                "Tie in loadScore → select by instanceId lexicographic order");
    }
}
