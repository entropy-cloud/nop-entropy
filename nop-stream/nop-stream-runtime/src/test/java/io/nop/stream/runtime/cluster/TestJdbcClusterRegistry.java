/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestJdbcClusterRegistry {

    private static HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private JdbcClusterRegistry registry;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));

        // Drop tables to start clean
        dropTableSilently("nop_stream_task_assignment");
        dropTableSilently("nop_stream_node");
        dropTableSilently("nop_stream_coordinator");

        registry = new JdbcClusterRegistry(jdbcTemplate);
    }

    private void dropTableSilently(String tableName) {
        try {
            SQL dropSql = SQL.begin().sql("DROP TABLE IF EXISTS " + tableName).end();
            jdbcTemplate.executeUpdate(dropSql);
        } catch (Exception ignored) {
            // Best-effort cleanup before each test; table may not exist on first run.
        }
    }

    // ---- Coordinator tests ----

    @Test
    void testRegisterAndGetCoordinator() {
        registry.registerCoordinator("job-1", "coord-1", "fence-1");

        CoordinatorInfo info = registry.getActiveCoordinator("job-1");
        assertNotNull(info);
        assertEquals("job-1", info.getJobId());
        assertEquals("coord-1", info.getCoordinatorId());
        assertEquals("fence-1", info.getFencingToken());
        assertTrue(info.getRegisteredAt() > 0);
    }

    @Test
    void testRegisterCoordinatorOverwrite() {
        registry.registerCoordinator("job-1", "coord-1", "fence-1");
        registry.registerCoordinator("job-1", "coord-2", "fence-2");

        CoordinatorInfo info = registry.getActiveCoordinator("job-1");
        assertNotNull(info);
        assertEquals("coord-2", info.getCoordinatorId());
        assertEquals("fence-2", info.getFencingToken());
    }

    @Test
    void testGetCoordinatorNotFound() {
        CoordinatorInfo info = registry.getActiveCoordinator("nonexistent");
        assertNull(info);
    }

    @Test
    void testMultipleJobsHaveSeparateCoordinators() {
        registry.registerCoordinator("job-1", "coord-1", "fence-1");
        registry.registerCoordinator("job-2", "coord-2", "fence-2");

        CoordinatorInfo info1 = registry.getActiveCoordinator("job-1");
        assertNotNull(info1);
        assertEquals("coord-1", info1.getCoordinatorId());

        CoordinatorInfo info2 = registry.getActiveCoordinator("job-2");
        assertNotNull(info2);
        assertEquals("coord-2", info2.getCoordinatorId());
    }

    // ---- Node registration tests ----

    @Test
    void testRegisterAndGetNode() {
        registry.registerNode("node-1", "host1:8080", 10);

        LeaseInfo lease = registry.getNodeLease("node-1");
        assertNotNull(lease);
        assertEquals("node-1", lease.getNodeId());
    }

    @Test
    void testRegisterNodeUpdate() {
        registry.registerNode("node-1", "host1:8080", 10);
        registry.registerNode("node-1", "host1:9090", 20);

        // Node should be updated (reregistered)
        LeaseInfo lease = registry.getNodeLease("node-1");
        assertNotNull(lease);
        assertEquals("node-1", lease.getNodeId());
    }

    @Test
    void testGetNodeLeaseNotRegistered() {
        LeaseInfo lease = registry.getNodeLease("nonexistent");
        assertNull(lease);
    }

    // ---- Lease tests ----

    @Test
    void testRenewLease() {
        registry.registerNode("node-1", "host1:8080", 10);

        boolean renewed = registry.renewLease("node-1", 30000);
        assertTrue(renewed);

        LeaseInfo lease = registry.getNodeLease("node-1");
        assertNotNull(lease);
        assertEquals("node-1", lease.getNodeId());
        assertTrue(lease.isActive());
        assertTrue(lease.getLeaseExpireAt() > System.currentTimeMillis());
    }

    @Test
    void testRenewLeaseNonExistentNode() {
        boolean renewed = registry.renewLease("nonexistent", 30000);
        assertFalse(renewed);
    }

    @Test
    void testGetActiveNodesEmpty() {
        List<NodeInfo> nodes = registry.getActiveNodes();
        assertNotNull(nodes);
        assertTrue(nodes.isEmpty());
    }

    @Test
    void testGetActiveNodesWithValidLease() {
        registry.registerNode("node-1", "host1:8080", 10);
        registry.renewLease("node-1", 60000);

        registry.registerNode("node-2", "host2:8080", 5);
        registry.renewLease("node-2", 60000);

        List<NodeInfo> activeNodes = registry.getActiveNodes();
        assertEquals(2, activeNodes.size());
    }

    @Test
    void testGetActiveNodesExcludesExpiredLease() {
        registry.registerNode("node-1", "host1:8080", 10);
        // Renew with a very short timeout that immediately expires
        registry.renewLease("node-1", -1);

        List<NodeInfo> activeNodes = registry.getActiveNodes();
        assertTrue(activeNodes.isEmpty());
    }

    // ---- Task assignment tests ----

    @Test
    void testAssignAndGetTask() {
        registry.registerCoordinator("job-1", "coord-1", "fence-1");
        registry.assignTask("job-1", "vertex-1", 0, "node-1", "attempt-1", "fence-1");

        TaskAssignment assignment = registry.getTaskAssignment("job-1", "vertex-1", 0);
        assertNotNull(assignment);
        assertEquals("job-1", assignment.getJobId());
        assertEquals("vertex-1", assignment.getVertexId());
        assertEquals(0, assignment.getSubtaskIndex());
        assertEquals("node-1", assignment.getNodeId());
        assertEquals("attempt-1", assignment.getAttemptId());
        assertEquals("fence-1", assignment.getFencingToken());
        assertTrue(assignment.getAssignedAt() > 0);
    }

    @Test
    void testAssignTaskPreservesAttemptHistory() {
        // G56: assignTask no longer overwrites; it appends to attempt history.
        registry.assignTask("job-1", "vertex-1", 0, "node-1", "attempt-1", "fence-1", 1);
        registry.assignTask("job-1", "vertex-1", 0, "node-2", "attempt-2", "fence-2", 2);

        // Latest = attempt 2
        TaskAssignment latest = registry.getTaskAssignment("job-1", "vertex-1", 0);
        assertNotNull(latest);
        assertEquals("node-2", latest.getNodeId());
        assertEquals("attempt-2", latest.getAttemptId());
        assertEquals("fence-2", latest.getFencingToken());
        assertEquals(2, latest.getAttemptNumber());

        // Full history preserved
        List<TaskAssignment> history = registry.getAttemptHistory("job-1", "vertex-1", 0);
        assertEquals(2, history.size());
        assertEquals(1, history.get(0).getAttemptNumber());
        assertEquals(2, history.get(1).getAttemptNumber());
    }

    @Test
    void testAttemptHistoryEmptyForUnknownTask() {
        List<TaskAssignment> history = registry.getAttemptHistory("nope", "nope", 99);
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void testGetTaskAssignmentNotFound() {
        TaskAssignment assignment = registry.getTaskAssignment("nonexistent", "vertex-1", 0);
        assertNull(assignment);
    }

    @Test
    void testRemoveTaskAssignment() {
        registry.assignTask("job-1", "vertex-1", 0, "node-1", "attempt-1", "fence-1");
        assertNotNull(registry.getTaskAssignment("job-1", "vertex-1", 0));

        registry.removeTaskAssignment("job-1", "vertex-1", 0);
        assertNull(registry.getTaskAssignment("job-1", "vertex-1", 0));
    }

    @Test
    void testRemoveNonExistentTaskAssignment() {
        assertDoesNotThrow(() -> registry.removeTaskAssignment("nonexistent", "vertex-1", 0));
    }

    @Test
    void testMultipleSubtaskAssignments() {
        registry.assignTask("job-1", "vertex-1", 0, "node-1", "attempt-1", "fence-1");
        registry.assignTask("job-1", "vertex-1", 1, "node-2", "attempt-1", "fence-1");
        registry.assignTask("job-1", "vertex-1", 2, "node-1", "attempt-1", "fence-1");

        TaskAssignment a0 = registry.getTaskAssignment("job-1", "vertex-1", 0);
        assertNotNull(a0);
        assertEquals("node-1", a0.getNodeId());

        TaskAssignment a1 = registry.getTaskAssignment("job-1", "vertex-1", 1);
        assertNotNull(a1);
        assertEquals("node-2", a1.getNodeId());

        TaskAssignment a2 = registry.getTaskAssignment("job-1", "vertex-1", 2);
        assertNotNull(a2);
        assertEquals("node-1", a2.getNodeId());
    }

    @Test
    void testRemoveOnlyOneSubtaskAssignment() {
        registry.assignTask("job-1", "vertex-1", 0, "node-1", "attempt-1", "fence-1");
        registry.assignTask("job-1", "vertex-1", 1, "node-2", "attempt-1", "fence-1");

        registry.removeTaskAssignment("job-1", "vertex-1", 0);

        assertNull(registry.getTaskAssignment("job-1", "vertex-1", 0));
        assertNotNull(registry.getTaskAssignment("job-1", "vertex-1", 1));
    }

    // ---- End-to-end scenario ----

    @Test
    void testFullClusterLifecycle() {
        // 1. Register coordinator
        registry.registerCoordinator("job-e2e", "coord-e2e", "token-e2e");
        CoordinatorInfo coord = registry.getActiveCoordinator("job-e2e");
        assertNotNull(coord);
        assertEquals("coord-e2e", coord.getCoordinatorId());

        // 2. Register nodes
        registry.registerNode("node-a", "10.0.0.1:8080", 4);
        registry.registerNode("node-b", "10.0.0.2:8080", 8);

        // 3. Renew leases
        assertTrue(registry.renewLease("node-a", 30000));
        assertTrue(registry.renewLease("node-b", 30000));

        // 4. Verify active nodes
        List<NodeInfo> activeNodes = registry.getActiveNodes();
        assertEquals(2, activeNodes.size());

        // 5. Assign tasks (G56: explicit attempt numbers)
        registry.assignTask("job-e2e", "source", 0, "node-a", "att-1", "token-e2e", 1);
        registry.assignTask("job-e2e", "source", 1, "node-b", "att-1", "token-e2e", 1);
        registry.assignTask("job-e2e", "sink", 0, "node-a", "att-1", "token-e2e", 1);

        // 6. Verify assignments
        assertNotNull(registry.getTaskAssignment("job-e2e", "source", 0));
        assertNotNull(registry.getTaskAssignment("job-e2e", "source", 1));
        assertNotNull(registry.getTaskAssignment("job-e2e", "sink", 0));

        // 7. Reassign a task (failover scenario) — append new attempt
        registry.assignTask("job-e2e", "source", 1, "node-a", "att-2", "token-e2e-v2", 2);
        TaskAssignment reassigned = registry.getTaskAssignment("job-e2e", "source", 1);
        assertNotNull(reassigned);
        assertEquals("node-a", reassigned.getNodeId());
        assertEquals("att-2", reassigned.getAttemptId());
        assertEquals("token-e2e-v2", reassigned.getFencingToken());
        assertEquals(2, reassigned.getAttemptNumber());

        // Attempt history preserves both attempts
        List<TaskAssignment> source1History = registry.getAttemptHistory("job-e2e", "source", 1);
        assertEquals(2, source1History.size());

        // 8. Remove completed task
        registry.removeTaskAssignment("job-e2e", "sink", 0);
        assertNull(registry.getTaskAssignment("job-e2e", "sink", 0));

        // 9. Coordinator takeover
        registry.registerCoordinator("job-e2e", "coord-e2e-new", "token-e2e-v3");
        CoordinatorInfo newCoord = registry.getActiveCoordinator("job-e2e");
        assertNotNull(newCoord);
        assertEquals("coord-e2e-new", newCoord.getCoordinatorId());
    }
}
