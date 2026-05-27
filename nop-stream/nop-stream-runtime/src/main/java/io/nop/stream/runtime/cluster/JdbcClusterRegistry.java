/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;

@Internal
public class JdbcClusterRegistry implements ClusterRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcClusterRegistry.class);

    private static final String COORDINATOR_TABLE = "nop_stream_coordinator";
    private static final String NODE_TABLE = "nop_stream_node";
    private static final String TASK_ASSIGNMENT_TABLE = "nop_stream_task_assignment";

    private static final String DEFAULT_QUERY_SPACE = "default";

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;

    private volatile boolean tablesInitialized;

    public JdbcClusterRegistry(IJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_QUERY_SPACE);
    }

    public JdbcClusterRegistry(IJdbcTemplate jdbcTemplate, String querySpace) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : DEFAULT_QUERY_SPACE;
    }

    @Override
    public void registerCoordinator(String jobId, String coordinatorId, String fencingToken) {
        ensureTables();

        long now = System.currentTimeMillis();

        // Delete existing coordinator for this job first (upsert pattern)
        SQL deleteSql = SQL.begin().name("deleteCoordinator").querySpace(querySpace)
                .sql("DELETE FROM " + COORDINATOR_TABLE + " WHERE job_id = ?", jobId)
                .end();

        SQL insertSql = SQL.begin().name("insertCoordinator").querySpace(querySpace)
                .sql("INSERT INTO " + COORDINATOR_TABLE +
                                " (job_id, coordinator_id, fencing_token, registered_at) VALUES (?,?,?,?)",
                        jobId, coordinatorId, fencingToken, now)
                .end();

        jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
            jdbcTemplate.executeUpdate(deleteSql);
            jdbcTemplate.executeUpdate(insertSql);
            return null;
        });

        LOG.debug("Registered coordinator {} for job {} with fencing token {}", coordinatorId, jobId, fencingToken);
    }

    @Override
    public CoordinatorInfo getActiveCoordinator(String jobId) {
        if (!coordinatorTableExists()) {
            return null;
        }

        SQL sql = SQL.begin().name("getActiveCoordinator").querySpace(querySpace)
                .sql("SELECT job_id, coordinator_id, fencing_token, registered_at FROM " + COORDINATOR_TABLE +
                        " WHERE job_id = ?", jobId)
                .end();

        return queryFirst(sql, this::mapCoordinatorInfo);
    }

    @Override
    public void registerNode(String nodeId, String endpoint, int capacity) {
        ensureTables();

        long now = System.currentTimeMillis();

        // Check if node already exists
        SQL existsSql = SQL.begin().name("nodeExists").querySpace(querySpace)
                .sql("SELECT 1 FROM " + NODE_TABLE + " WHERE node_id = ?", nodeId)
                .end();

        boolean exists = jdbcTemplate.exists(existsSql);

        if (exists) {
            // Update existing node
            SQL updateSql = SQL.begin().name("updateNode").querySpace(querySpace)
                    .sql("UPDATE " + NODE_TABLE +
                                    " SET endpoint = ?, capacity = ?, last_heartbeat_at = ? WHERE node_id = ?",
                            endpoint, capacity, now, nodeId)
                    .end();
            jdbcTemplate.executeUpdate(updateSql);
        } else {
            // Insert new node
            SQL insertSql = SQL.begin().name("insertNode").querySpace(querySpace)
                    .sql("INSERT INTO " + NODE_TABLE +
                                    " (node_id, endpoint, capacity, registered_at, last_heartbeat_at, lease_expire_at) VALUES (?,?,?,?,?,?)",
                            nodeId, endpoint, capacity, now, now, 0L)
                    .end();
            jdbcTemplate.executeUpdate(insertSql);
        }

        LOG.debug("Registered node {} at endpoint {} with capacity {}", nodeId, endpoint, capacity);
    }

    @Override
    public boolean renewLease(String nodeId, long leaseTimeoutMs) {
        if (!nodeTableExists()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long expireAt = now + leaseTimeoutMs;

        SQL sql = SQL.begin().name("renewLease").querySpace(querySpace)
                .sql("UPDATE " + NODE_TABLE +
                                " SET last_heartbeat_at = ?, lease_expire_at = ? WHERE node_id = ?",
                        now, expireAt, nodeId)
                .end();

        long rows = jdbcTemplate.executeUpdate(sql);
        return rows > 0;
    }

    @Override
    public LeaseInfo getNodeLease(String nodeId) {
        if (!nodeTableExists()) {
            return null;
        }

        SQL sql = SQL.begin().name("getNodeLease").querySpace(querySpace)
                .sql("SELECT node_id, last_heartbeat_at, lease_expire_at FROM " + NODE_TABLE +
                        " WHERE node_id = ?", nodeId)
                .end();

        return queryFirst(sql, this::mapLeaseInfo);
    }

    @Override
    public List<NodeInfo> getActiveNodes() {
        if (!nodeTableExists()) {
            return new ArrayList<>();
        }

        long now = System.currentTimeMillis();

        SQL sql = SQL.begin().name("getActiveNodes").querySpace(querySpace)
                .sql("SELECT node_id, endpoint, capacity, registered_at, last_heartbeat_at FROM " + NODE_TABLE +
                        " WHERE lease_expire_at > ?", now)
                .end();

        List<NodeInfo> result = new ArrayList<>();
        jdbcTemplate.executeQuery(sql, dataSet -> {
            for (IDataRow row : dataSet) {
                result.add(mapNodeInfoFromRow(row));
            }
            return null;
        });
        return result;
    }

    @Override
    public void assignTask(String jobId, String vertexId, int subtaskIndex,
                           String nodeId, String attemptId, String fencingToken) {
        ensureTables();

        long now = System.currentTimeMillis();

        // Delete existing assignment for this task (upsert pattern)
        SQL deleteSql = SQL.begin().name("deleteTaskAssignment").querySpace(querySpace)
                .sql("DELETE FROM " + TASK_ASSIGNMENT_TABLE +
                                " WHERE job_id = ? AND vertex_id = ? AND subtask_index = ?",
                        jobId, vertexId, subtaskIndex)
                .end();

        SQL insertSql = SQL.begin().name("insertTaskAssignment").querySpace(querySpace)
                .sql("INSERT INTO " + TASK_ASSIGNMENT_TABLE +
                                " (job_id, vertex_id, subtask_index, node_id, attempt_id, fencing_token, assigned_at)" +
                                " VALUES (?,?,?,?,?,?,?)",
                        jobId, vertexId, subtaskIndex, nodeId, attemptId, fencingToken, now)
                .end();

        jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
            jdbcTemplate.executeUpdate(deleteSql);
            jdbcTemplate.executeUpdate(insertSql);
            return null;
        });

        LOG.debug("Assigned task {}/{}/{} to node {} with attempt {}", jobId, vertexId, subtaskIndex, nodeId, attemptId);
    }

    @Override
    public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        if (!taskAssignmentTableExists()) {
            return null;
        }

        SQL sql = SQL.begin().name("getTaskAssignment").querySpace(querySpace)
                .sql("SELECT job_id, vertex_id, subtask_index, node_id, attempt_id, fencing_token, assigned_at" +
                        " FROM " + TASK_ASSIGNMENT_TABLE +
                        " WHERE job_id = ? AND vertex_id = ? AND subtask_index = ?",
                        jobId, vertexId, subtaskIndex)
                .end();

        return queryFirst(sql, this::mapTaskAssignment);
    }

    @Override
    public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        if (!taskAssignmentTableExists()) {
            return;
        }

        SQL sql = SQL.begin().name("removeTaskAssignment").querySpace(querySpace)
                .sql("DELETE FROM " + TASK_ASSIGNMENT_TABLE +
                                " WHERE job_id = ? AND vertex_id = ? AND subtask_index = ?",
                        jobId, vertexId, subtaskIndex)
                .end();

        jdbcTemplate.executeUpdate(sql);
    }

    // ---- Table initialization ----

    private boolean coordinatorTableExists() {
        return tableExists(COORDINATOR_TABLE);
    }

    private boolean nodeTableExists() {
        return tableExists(NODE_TABLE);
    }

    private boolean taskAssignmentTableExists() {
        return tableExists(TASK_ASSIGNMENT_TABLE);
    }

    private boolean tableExists(String tableName) {
        try {
            return jdbcTemplate.existsTable(querySpace, tableName);
        } catch (Exception e) {
            LOG.debug("Failed to check table existence: {}", tableName, e);
            return false;
        }
    }

    private void ensureTables() {
        if (tablesInitialized) {
            return;
        }
        synchronized (this) {
            if (tablesInitialized) {
                return;
            }

            if (coordinatorTableExists() && nodeTableExists() && taskAssignmentTableExists()) {
                tablesInitialized = true;
                return;
            }

            jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                createCoordinatorTable();
                createNodeTable();
                createTaskAssignmentTable();
                return null;
            });

            tablesInitialized = true;
        }
    }

    private void createCoordinatorTable() {
        if (coordinatorTableExists()) {
            return;
        }

        String ddl = "CREATE TABLE " + COORDINATOR_TABLE + " (" +
                "job_id VARCHAR(255) NOT NULL, " +
                "coordinator_id VARCHAR(255) NOT NULL, " +
                "fencing_token VARCHAR(255) NOT NULL, " +
                "registered_at BIGINT NOT NULL, " +
                "PRIMARY KEY (job_id)" +
                ")";

        SQL sql = SQL.begin().name("createCoordinatorTable").querySpace(querySpace)
                .sql(ddl).end();
        jdbcTemplate.executeUpdate(sql);
        LOG.info("Created table {}", COORDINATOR_TABLE);
    }

    private void createNodeTable() {
        if (nodeTableExists()) {
            return;
        }

        String ddl = "CREATE TABLE " + NODE_TABLE + " (" +
                "node_id VARCHAR(255) NOT NULL, " +
                "endpoint VARCHAR(512) NOT NULL, " +
                "capacity INT NOT NULL DEFAULT 1, " +
                "registered_at BIGINT NOT NULL, " +
                "last_heartbeat_at BIGINT NOT NULL, " +
                "lease_expire_at BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (node_id)" +
                ")";

        SQL sql = SQL.begin().name("createNodeTable").querySpace(querySpace)
                .sql(ddl).end();
        jdbcTemplate.executeUpdate(sql);

        try {
            SQL idxSql = SQL.begin().name("createNodeLeaseIdx").querySpace(querySpace)
                    .sql("CREATE INDEX idx_node_lease_expire ON " + NODE_TABLE + " (lease_expire_at)")
                    .end();
            jdbcTemplate.executeUpdate(idxSql);
        } catch (Exception e) {
            LOG.debug("Index idx_node_lease_expire may already exist, ignoring", e);
        }

        LOG.info("Created table {}", NODE_TABLE);
    }

    private void createTaskAssignmentTable() {
        if (taskAssignmentTableExists()) {
            return;
        }

        String ddl = "CREATE TABLE " + TASK_ASSIGNMENT_TABLE + " (" +
                "job_id VARCHAR(255) NOT NULL, " +
                "vertex_id VARCHAR(255) NOT NULL, " +
                "subtask_index INT NOT NULL, " +
                "node_id VARCHAR(255) NOT NULL, " +
                "attempt_id VARCHAR(255) NOT NULL, " +
                "fencing_token VARCHAR(255) NOT NULL, " +
                "assigned_at BIGINT NOT NULL, " +
                "PRIMARY KEY (job_id, vertex_id, subtask_index)" +
                ")";

        SQL sql = SQL.begin().name("createTaskAssignmentTable").querySpace(querySpace)
                .sql(ddl).end();
        jdbcTemplate.executeUpdate(sql);

        try {
            SQL idxSql = SQL.begin().name("createTaskNodeIdx").querySpace(querySpace)
                    .sql("CREATE INDEX idx_task_node ON " + TASK_ASSIGNMENT_TABLE + " (node_id)")
                    .end();
            jdbcTemplate.executeUpdate(idxSql);
        } catch (Exception e) {
            LOG.debug("Index idx_task_node may already exist, ignoring", e);
        }

        LOG.info("Created table {}", TASK_ASSIGNMENT_TABLE);
    }

    // ---- Row mappers ----

    private CoordinatorInfo mapCoordinatorInfo(IDataSet dataSet) {
        for (IDataRow row : dataSet) {
            return new CoordinatorInfo(
                    row.getString(0),
                    row.getString(1),
                    row.getString(2),
                    getLong(row, 3)
            );
        }
        return null;
    }

    private LeaseInfo mapLeaseInfo(IDataSet dataSet) {
        for (IDataRow row : dataSet) {
            long leaseExpireAt = getLong(row, 2);
            long now = System.currentTimeMillis();
            boolean active = leaseExpireAt > now;
            return new LeaseInfo(
                    row.getString(0),
                    getLong(row, 1),
                    leaseExpireAt,
                    active
            );
        }
        return null;
    }

    private NodeInfo mapNodeInfoFromRow(IDataRow row) {
        return new NodeInfo(
                row.getString(0),
                row.getString(1),
                row.getInt(2),
                getLong(row, 3),
                getLong(row, 4)
        );
    }

    private TaskAssignment mapTaskAssignment(IDataSet dataSet) {
        for (IDataRow row : dataSet) {
            return new TaskAssignment(
                    row.getString(0),
                    row.getString(1),
                    row.getInt(2),
                    row.getString(3),
                    row.getString(4),
                    row.getString(5),
                    getLong(row, 6)
            );
        }
        return null;
    }

    private long getLong(IDataRow row, int index) {
        Object val = row.getObject(index);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 0L;
    }

    private <T> T queryFirst(SQL sql, Function<IDataSet, T> mapper) {
        return jdbcTemplate.executeQuery(sql, mapper);
    }
}
