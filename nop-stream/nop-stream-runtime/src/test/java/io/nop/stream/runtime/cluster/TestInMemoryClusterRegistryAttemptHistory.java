/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G56: verifies that {@link InMemoryClusterRegistry} preserves attempt history
 * rather than overwriting prior assignments. The same subtask's multiple
 * attempts must all remain retrievable via {@code getAttemptHistory} in
 * monotonic attemptNumber order.
 */
class TestInMemoryClusterRegistryAttemptHistory {

    private InMemoryClusterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryClusterRegistry();
    }

    @Test
    void firstAttemptIsRetrievable() {
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-1", 1L, 1);

        TaskAssignment latest = registry.getTaskAssignment("job-1", "v-1", 0);
        assertNotNull(latest);
        assertEquals("node-1", latest.getNodeId());
        assertEquals("att-1", latest.getAttemptId());
        assertEquals(1, latest.getAttemptNumber());

        List<TaskAssignment> history = registry.getAttemptHistory("job-1", "v-1", 0);
        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getAttemptNumber());
    }

    @Test
    void multipleAttemptsAreAppendedNotOverwritten() {
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-1", 1L, 1);
        registry.assignTask("job-1", "v-1", 0, "node-2", "att-2", 2L, 2);
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-3", 3L, 3);

        // Latest = attempt 3
        TaskAssignment latest = registry.getTaskAssignment("job-1", "v-1", 0);
        assertNotNull(latest);
        assertEquals(3, latest.getAttemptNumber());
        assertEquals("att-3", latest.getAttemptId());

        // Full history = all 3 attempts, monotonic
        List<TaskAssignment> history = registry.getAttemptHistory("job-1", "v-1", 0);
        assertEquals(3, history.size());
        assertEquals(1, history.get(0).getAttemptNumber());
        assertEquals(2, history.get(1).getAttemptNumber());
        assertEquals(3, history.get(2).getAttemptNumber());
    }

    @Test
    void differentSubtasksHaveIndependentHistory() {
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-a", 1L, 1);
        registry.assignTask("job-1", "v-1", 1, "node-2", "att-b", 1L, 1);
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-c", 2L, 2);

        assertEquals(2, registry.getAttemptHistory("job-1", "v-1", 0).size());
        assertEquals(1, registry.getAttemptHistory("job-1", "v-1", 1).size());
    }

    @Test
    void emptyHistoryForUnknownTask() {
        List<TaskAssignment> history = registry.getAttemptHistory("nope", "nope", 99);
        assertNotNull(history);
        assertTrue(history.isEmpty());

        assertNull(registry.getTaskAssignment("nope", "nope", 99));
    }

    @Test
    void removeTaskAssignmentClearsHistory() {
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-1", 1L, 1);
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-2", 2L, 2);
        assertEquals(2, registry.getAttemptHistory("job-1", "v-1", 0).size());

        registry.removeTaskAssignment("job-1", "v-1", 0);
        assertTrue(registry.getAttemptHistory("job-1", "v-1", 0).isEmpty());
        assertNull(registry.getTaskAssignment("job-1", "v-1", 0));
    }

    @Test
    void historyIsUnmodifiable() {
        registry.assignTask("job-1", "v-1", 0, "node-1", "att-1", 1L, 1);
        List<TaskAssignment> history = registry.getAttemptHistory("job-1", "v-1", 0);
        assertEquals(1, history.size());

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> history.add(new TaskAssignment()));
    }
}
