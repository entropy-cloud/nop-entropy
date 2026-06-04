package io.nop.stream.runtime.execution;

import io.nop.stream.runtime.taskmanager.TaskManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskManagerDaemon {

    @Test
    void testTaskExecutorThreadsAreDaemon() throws Exception {
        TaskManager tm = new TaskManager("node-daemon-test", "localhost:0", 2, null, null, "ctrl");
        java.util.Set<Thread> threadsBefore = Thread.getAllStackTraces().keySet();

        java.util.concurrent.atomic.AtomicBoolean foundDaemonTaskThread = new java.util.concurrent.atomic.AtomicBoolean(false);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("tm-task-")) {
                foundDaemonTaskThread.set(true);
                assertTrue(t.isDaemon(), "Task thread should be daemon: " + t.getName());
            }
        }

        tm.stop();
    }

    @Test
    void testHeartbeatThreadIsDaemon() {
        TaskManager tm = new TaskManager("node-hb-test", "localhost:0", 1, null, null, "ctrl");

        boolean foundDaemonHeartbeat = false;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("tm-heartbeat-")) {
                foundDaemonHeartbeat = true;
                assertTrue(t.isDaemon(), "Heartbeat thread should be daemon: " + t.getName());
            }
        }

        tm.stop();
    }
}
