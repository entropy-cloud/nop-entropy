package io.nop.duckdb;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 resume semantics: a task running duckdb steps suspends mid-way (simulated kill:
 * runtime and engine are discarded), then resumes from the state store with a fresh
 * engine reopening the same .duckdb file - the verify step must read the pre-kill data.
 */
public class TestDuckDbResume extends BaseTestCase {

    @BeforeAll
    public static void init() {
        // <suspend> without an executor attribute suspends synchronously (SuspendTaskStep),
        // so no executor bean is needed - replacing the BeanContainer would hide the
        // _vfs-wired nopDuckDbSqlTaskStep bean from the duckdb steps.
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @Timeout(120)
    public void testSuspendKillResumeDataConsistent() throws Exception {
        Path db = Files.createTempDirectory("duckdb-wi4-resume").resolve("work.duckdb");

        // fresh execute: setup step runs, task suspends at <suspend>
        TestSnapshotTaskStateStore store = new TestSnapshotTaskStateStore();
        TaskFlowManagerImpl manager1 = new TaskFlowManagerImpl();
        manager1.setNonPersistStateStore(store);
        manager1.setTaskStateStore(store);

        ITask task1 = manager1.loadTaskFromPath("/nop/duckdb/task/resume.task.xml");
        ITaskRuntime taskRt1 = manager1.newTaskRuntime(task1, false, null);
        taskRt1.setInput("dbPath", db.toString());
        TaskStepReturn ret1 = task1.execute(taskRt1);
        if (ret1.isAsync()) {
            ret1 = ret1.sync();
        }
        assertTrue(ret1.isSuspend(), "task must suspend at <suspend>");
        String taskInstanceId = taskRt1.getTaskInstanceId();

        // ---- simulated kill: runtime and engine discarded; only the state store persists ----

        // resume with a fresh manager sharing the store (DB-store surrogate) and a fresh engine
        TaskFlowManagerImpl manager2 = new TaskFlowManagerImpl();
        manager2.setNonPersistStateStore(store);
        manager2.setTaskStateStore(store);

        assertNotNull(store.loadTaskState(taskInstanceId, manager2.newTaskRuntime(
                        manager2.loadTaskFromPath("/nop/duckdb/task/resume.task.xml"), false, null)),
                "state store must persist the task state across the simulated restart");

        ITask task2 = manager2.loadTaskFromPath("/nop/duckdb/task/resume.task.xml");
        ITaskRuntime resumeRt = manager2.getTaskRuntime(taskInstanceId, null, null);
        // launch inputs are replayed by the orchestrator on resume (the snapshot store is a
        // minimal surrogate and does not carry task inputs; DB-backed stores persist them)
        resumeRt.setInput("dbPath", db.toString());
        TaskStepReturn ret2 = task2.execute(resumeRt);
        if (ret2.isAsync()) {
            ret2 = ret2.sync();
        }
        Map<String, Object> outputs = ret2.syncGetOutputs();
        assertEquals(1, ((Number) outputs.get("verifyRows")).intValue(),
                "verify step must read the pre-kill row from the .duckdb working set");
    }
}
