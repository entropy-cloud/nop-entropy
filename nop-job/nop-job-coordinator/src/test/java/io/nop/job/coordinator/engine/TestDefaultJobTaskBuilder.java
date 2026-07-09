package io.nop.job.coordinator.engine;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDefaultJobTaskBuilder extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    void testBuildSingleTask() {
        DefaultJobTaskBuilder builder = new DefaultJobTaskBuilder();
        builder.setDaoProvider(daoProvider);

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("fire-1");
        fire.setPartitionIndex((short) 1);
        fire.setExecutorKind("rpc");
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("serviceName", "myService"));

        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(1, tasks.size());
        NopJobTask task = tasks.get(0);
        assertEquals("fire-1", task.getJobFireId());
        assertEquals(1, task.getTaskNo());
        assertEquals(0, task.getTaskStatus());
        assertEquals((short) 1, task.getPartitionIndex());
        // taskPayload is the task-level params slot; default single task carries none.
        assertNull(task.getTaskPayload());
        // Effective params = fire snapshot (fire passed explicitly, as in worker/cancel paths).
        assertEquals("myService", task.getEffectiveParams(fire).get("serviceName"));
    }

    @Test
    void testBuildWithNullSnapshots() {
        DefaultJobTaskBuilder builder = new DefaultJobTaskBuilder();
        builder.setDaoProvider(daoProvider);

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("fire-2");
        fire.setPartitionIndex((short) 0);

        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(1, tasks.size());
        NopJobTask task = tasks.get(0);
        assertNull(task.getTaskPayload());
        assertTrue(task.getEffectiveParams(fire).isEmpty());
    }

    /**
     * AR-91：single 模式 task 的 workerInstanceId 必须为 NULL（走 competing-consumer IS NULL 分支，
     * 被 enforceAttribution=true 的非同地部署 worker 认领），不再写 coordinator hostId 造成饥饿。
     */
    @Test
    void testSingleTaskLeavesWorkerInstanceIdNull() {
        DefaultJobTaskBuilder builder = new DefaultJobTaskBuilder();
        builder.setDaoProvider(daoProvider);

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("fire-ar91");
        fire.setPartitionIndex((short) 1);

        List<NopJobTask> tasks = builder.buildTasks(fire);

        NopJobTask task = tasks.get(0);
        assertNull(task.getWorkerInstanceId(),
                "single-mode task must leave workerInstanceId NULL (AR-91), not the coordinator hostId");
    }
}
