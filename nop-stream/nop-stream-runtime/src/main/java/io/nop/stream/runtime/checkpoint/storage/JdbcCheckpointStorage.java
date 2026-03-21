/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC database storage implementation using JSON serialization.
 */
public class JdbcCheckpointStorage implements ICheckpointStorage {

    private static final String TABLE_NAME = "stream_checkpoint";

    private final DataSource dataSource;

    public JdbcCheckpointStorage(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureTableExists();
    }

    @Override
    public String getName() {
        return "JdbcCheckpointStorage";
    }

    @Override
    public String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception {
        String sql = "INSERT INTO " + TABLE_NAME + " " +
                "(job_id, pipeline_id, checkpoint_id, checkpoint_type, trigger_timestamp, " +
                "completed_timestamp, state_data) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            byte[] stateData = serializeCheckpoint(checkpoint);

            stmt.setLong(1, checkpoint.getJobId());
            stmt.setInt(2, checkpoint.getPipelineId());
            stmt.setLong(3, checkpoint.getCheckpointId());
            stmt.setString(4, checkpoint.getCheckpointType().name());
            stmt.setLong(5, checkpoint.getTriggerTimestamp());
            stmt.setLong(6, checkpoint.getCompletedTimestamp());
            stmt.setBytes(7, stateData);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return String.valueOf(rs.getLong(1));
                }
            }
            return checkpoint.getJobId() + "_" + checkpoint.getCheckpointId();
        }
    }

    @Override
    public CompletedCheckpoint getLatestCheckpoint(long jobId, int pipelineId) throws Exception {
        String sql = "SELECT state_data FROM " + TABLE_NAME + " " +
                "WHERE job_id = ? AND pipeline_id = ? " +
                "ORDER BY checkpoint_id DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, jobId);
            stmt.setInt(2, pipelineId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] data = rs.getBytes(1);
                    return deserializeCheckpoint(data);
                }
                return null;
            }
        }
    }

    @Override
    public List<CompletedCheckpoint> getAllCheckpoints(long jobId) throws Exception {
        String sql = "SELECT state_data FROM " + TABLE_NAME + " " +
                "WHERE job_id = ? ORDER BY checkpoint_id DESC";

        List<CompletedCheckpoint> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, jobId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] data = rs.getBytes(1);
                    CompletedCheckpoint cp = deserializeCheckpoint(data);
                if (cp != null) {
                    result.add(cp);
                }
            }
            }
        }
        return result;
    }

    @Override
    public List<CompletedCheckpoint> getLatestCheckpoints(long jobId, int count) throws Exception {
        String sql = "SELECT state_data FROM " + TABLE_NAME + " " +
                "WHERE job_id = ? ORDER BY checkpoint_id DESC LIMIT ?";

        List<CompletedCheckpoint> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, jobId);
            stmt.setInt(2, count);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] data = rs.getBytes(1);
                    CompletedCheckpoint cp = deserializeCheckpoint(data);
                if (cp != null) {
                    result.add(cp);
                }
            }
            }
        }
        return result;
    }

    @Override
    public void deleteCheckpoint(long jobId, int pipelineId, long checkpointId) throws Exception {
        String sql = "DELETE FROM " + TABLE_NAME + " " +
                "WHERE job_id = ? AND pipeline_id = ? AND checkpoint_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, jobId);
            stmt.setInt(2, pipelineId);
            stmt.setLong(3, checkpointId);
            stmt.executeUpdate();
        }
    }

    @Override
    public void deleteAllCheckpoints(long jobId) throws Exception {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE job_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, jobId);
            stmt.executeUpdate();
        }
    }

    @Override
    public int getCheckpointCount(long jobId) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE job_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, jobId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
        return JsonTool.serialize(checkpoint, false).getBytes(StandardCharsets.UTF_8);
    }

    private CompletedCheckpoint deserializeCheckpoint(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return JsonTool.parseBeanFromText(new String(data, StandardCharsets.UTF_8), CompletedCheckpoint.class);
    }

    private void ensureTableExists() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "sid BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "job_id BIGINT NOT NULL, " +
                "pipeline_id INT NOT NULL, " +
                "checkpoint_id BIGINT NOT NULL, " +
                "checkpoint_type VARCHAR(50) NOT NULL, " +
                "trigger_timestamp BIGINT NOT NULL, " +
                "completed_timestamp BIGINT NOT NULL, " +
                "state_data BLOB, " +
                "created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_job_pipeline (job_id, pipeline_id), " +
                "INDEX idx_checkpoint_id (checkpoint_id)" +
                ")";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create checkpoint table", e);
        }
    }
}
