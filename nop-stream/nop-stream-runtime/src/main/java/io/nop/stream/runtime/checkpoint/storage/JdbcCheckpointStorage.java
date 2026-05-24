/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataRow;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.model.StreamModelFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Internal
public class JdbcCheckpointStorage implements ICheckpointStorage {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcCheckpointStorage.class);

    private static final String TABLE_NAME = "stream_checkpoint";
    private static final String EPOCH_TABLE_NAME = "stream_epoch_manifest";

    private static final String DEFAULT_QUERY_SPACE = "default";

    private static long sidSequence = System.currentTimeMillis();

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private volatile boolean tableInitialized;
    private volatile boolean epochTableInitialized;

    public JdbcCheckpointStorage(IJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_QUERY_SPACE);
    }

    public JdbcCheckpointStorage(IJdbcTemplate jdbcTemplate, String querySpace) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : DEFAULT_QUERY_SPACE;
    }

    @Override
    public String getName() {
        return "JdbcCheckpointStorage";
    }

    @Override
    public String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception {
        ensureTable();
        byte[] stateData = serializeCheckpoint(checkpoint);
        long sid = nextSid();

        SQL sql = SQL.begin().name("storeCheckpoint").querySpace(querySpace)
                .sql("INSERT INTO " + TABLE_NAME +
                        " (sid, job_id, pipeline_id, checkpoint_id, checkpoint_type, trigger_timestamp, " +
                        "completed_timestamp, state_data) VALUES (?,?,?,?,?,?,?,?)",
                        sid,
                        checkpoint.getJobId(),
                        checkpoint.getPipelineId(),
                        checkpoint.getCheckpointId(),
                        checkpoint.getCheckpointType().name(),
                        checkpoint.getTriggerTimestamp(),
                        checkpoint.getCompletedTimestamp(),
                        stateData)
                .end();

        jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
            jdbcTemplate.executeUpdate(sql);
            return null;
        });

        return checkpoint.getJobId() + "_" + checkpoint.getCheckpointId();
    }

    @Override
    public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws Exception {
        if (!tableExists()) {
            return null;
        }

        SQL sql = SQL.begin().name("getLatestCheckpoint").querySpace(querySpace)
                .sql("SELECT state_data FROM " + TABLE_NAME +
                        " WHERE job_id = ? AND pipeline_id = ?" +
                        " ORDER BY checkpoint_id DESC LIMIT 1", jobId, pipelineId)
                .end();

        byte[][] result = {null};
        jdbcTemplate.executeQuery(sql, dataSet -> {
            for (IDataRow row : dataSet) {
                result[0] = row.getBytes(0);
                break;
            }
            return null;
        });
        return deserializeCheckpoint(result[0]);
    }

    @Override
    public List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws Exception {
        if (!tableExists()) {
            return Collections.emptyList();
        }

        SQL sql = SQL.begin().name("getAllCheckpoints").querySpace(querySpace)
                .sql("SELECT state_data FROM " + TABLE_NAME +
                        " WHERE job_id = ? ORDER BY checkpoint_id DESC", jobId)
                .end();

        List<CompletedCheckpoint> result = new ArrayList<>();
        jdbcTemplate.executeQuery(sql, dataSet -> {
            for (IDataRow row : dataSet) {
                byte[] data = row.getBytes(0);
                CompletedCheckpoint cp = deserializeCheckpoint(data);
                if (cp != null) {
                    result.add(cp);
                }
            }
            return null;
        });
        return result;
    }

    @Override
    public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws Exception {
        if (!tableExists()) {
            return Collections.emptyList();
        }

        SQL sql = SQL.begin().name("getLatestCheckpoints").querySpace(querySpace)
                .sql("SELECT state_data FROM " + TABLE_NAME +
                        " WHERE job_id = ? ORDER BY checkpoint_id DESC LIMIT ?", jobId, count)
                .end();

        List<CompletedCheckpoint> result = new ArrayList<>();
        jdbcTemplate.executeQuery(sql, dataSet -> {
            for (IDataRow row : dataSet) {
                byte[] data = row.getBytes(0);
                CompletedCheckpoint cp = deserializeCheckpoint(data);
                if (cp != null) {
                    result.add(cp);
                }
            }
            return null;
        });
        return result;
    }

    @Override
    public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws Exception {
        if (!tableExists()) {
            return;
        }

        SQL sql = SQL.begin().name("deleteCheckpoint").querySpace(querySpace)
                .sql("DELETE FROM " + TABLE_NAME +
                        " WHERE job_id = ? AND pipeline_id = ? AND checkpoint_id = ?",
                        jobId, pipelineId, checkpointId)
                .end();

        jdbcTemplate.executeUpdate(sql);
    }

    @Override
    public void deleteAllCheckpoints(String jobId) throws Exception {
        if (!tableExists()) {
            return;
        }

        SQL sql = SQL.begin().name("deleteAllCheckpoints").querySpace(querySpace)
                .sql("DELETE FROM " + TABLE_NAME + " WHERE job_id = ?", jobId)
                .end();

        jdbcTemplate.executeUpdate(sql);
    }

    @Override
    public int getCheckpointCount(String jobId) throws Exception {
        if (!tableExists()) {
            return 0;
        }

        SQL sql = SQL.begin().name("getCheckpointCount").querySpace(querySpace)
                .sql("SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE job_id = ?", jobId)
                .end();

        Integer count = jdbcTemplate.findInt(sql, 0);
        return count != null ? count : 0;
    }

    @Override
    public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws Exception {
        ensureTable();
        byte[] stateData = serializeCheckpoint(checkpoint);
        long sid = nextSid();

        SQL sql = SQL.begin().name("storeSavepoint").querySpace(querySpace)
                .sql("INSERT INTO " + TABLE_NAME +
                        " (sid, job_id, pipeline_id, checkpoint_id, checkpoint_type, trigger_timestamp, " +
                        "completed_timestamp, state_data, savepoint_path) VALUES (?,?,?,?,?,?,?,?,?)",
                        sid,
                        checkpoint.getJobId(),
                        checkpoint.getPipelineId(),
                        checkpoint.getCheckpointId(),
                        checkpoint.getCheckpointType().name(),
                        checkpoint.getTriggerTimestamp(),
                        checkpoint.getCompletedTimestamp(),
                        stateData,
                        targetPath)
                .end();

        jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
            jdbcTemplate.executeUpdate(sql);
            return null;
        });

        return targetPath;
    }

    @Override
    public CompletedCheckpoint loadSavepoint(String savepointPath) throws Exception {
        if (!tableExists()) {
            return null;
        }
        if (savepointPath == null || savepointPath.isEmpty()) {
            return null;
        }

        SQL sql = SQL.begin().name("loadSavepoint").querySpace(querySpace)
                .sql("SELECT state_data FROM " + TABLE_NAME +
                        " WHERE savepoint_path = ?" +
                        " ORDER BY checkpoint_id DESC LIMIT 1", savepointPath)
                .end();

        byte[][] result = {null};
        jdbcTemplate.executeQuery(sql, dataSet -> {
            for (IDataRow row : dataSet) {
                result[0] = row.getBytes(0);
                break;
            }
            return null;
        });
        return deserializeCheckpoint(result[0]);
    }

    @Override
    public SavepointMetadata loadSavepointMetadata(String savepointPath) throws Exception {
        CompletedCheckpoint checkpoint = loadSavepoint(savepointPath);
        if (checkpoint == null) {
            return null;
        }
        return SavepointMetadata.fromCompletedCheckpoint(checkpoint);
    }

    private boolean tableExists() {
        try {
            return jdbcTemplate.existsTable(querySpace, TABLE_NAME);
        } catch (Exception e) {
            LOG.debug("Failed to check table existence", e);
            return false;
        }
    }

    private void ensureTable() {
        if (tableInitialized) {
            return;
        }
        synchronized (this) {
            if (tableInitialized) {
                return;
            }

            if (tableExists()) {
                tableInitialized = true;
                return;
            }

            String ddl = "CREATE TABLE " + TABLE_NAME + " (" +
                    "sid BIGINT NOT NULL, " +
                    "job_id VARCHAR(255) NOT NULL, " +
                    "pipeline_id VARCHAR(255) NOT NULL, " +
                    "checkpoint_id BIGINT NOT NULL, " +
                    "checkpoint_type VARCHAR(50) NOT NULL, " +
                    "trigger_timestamp BIGINT NOT NULL, " +
                    "completed_timestamp BIGINT NOT NULL, " +
                    "state_data BLOB, " +
                    "savepoint_path VARCHAR(1024), " +
                    "created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (sid)" +
                    ")";

            SQL createTableSql = SQL.begin().name("createCheckpointTable").querySpace(querySpace)
                    .sql(ddl)
                    .end();

            jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                jdbcTemplate.executeUpdate(createTableSql);

                try {
                    SQL idx1 = SQL.begin().name("createIdxJobPipeline").querySpace(querySpace)
                            .sql("CREATE INDEX idx_job_pipeline ON " + TABLE_NAME + " (job_id, pipeline_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx1);
                } catch (Exception e) {
                    LOG.debug("Index idx_job_pipeline may already exist, ignoring", e);
                }

                try {
                    SQL idx2 = SQL.begin().name("createIdxCheckpointId").querySpace(querySpace)
                            .sql("CREATE INDEX idx_checkpoint_id ON " + TABLE_NAME + " (checkpoint_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx2);
                } catch (Exception e) {
                    LOG.debug("Index idx_checkpoint_id may already exist, ignoring", e);
                }

                return null;
            });
            tableInitialized = true;
        }
    }

    private byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
        Map<String, Object> serializable = new LinkedHashMap<>();
        serializable.put("jobId", checkpoint.getJobId());
        serializable.put("pipelineId", checkpoint.getPipelineId());
        serializable.put("checkpointId", checkpoint.getCheckpointId());
        serializable.put("triggerTimestamp", checkpoint.getTriggerTimestamp());
        serializable.put("completedTimestamp", checkpoint.getCompletedTimestamp());
        serializable.put("checkpointType", checkpoint.getCheckpointType().name());
        serializable.put("restored", checkpoint.isRestored());

        Map<String, Object> taskStatesMap = new LinkedHashMap<>();
        for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : checkpoint.getTaskStates().entrySet()) {
            String key = taskLocationToString(entry.getKey());
            taskStatesMap.put(key, entry.getValue());
        }
        serializable.put("taskStates", taskStatesMap);

        return JsonTool.serialize(serializable, false).getBytes(StandardCharsets.UTF_8);
    }

    private static String taskLocationToString(TaskLocation loc) {
        return loc.getJobId() + "|" + loc.getPipelineId() + "|" + loc.getVertexId() + "|" + loc.getTaskIndex();
    }

    private static TaskLocation stringToTaskLocation(String str) {
        String[] parts = str.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid TaskLocation string: " + str);
        }
        return new TaskLocation(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
    }

    private CompletedCheckpoint deserializeCheckpoint(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        Map<String, Object> map = JsonTool.parseMap(json);
        if (map == null) {
            return null;
        }

        String jobId = (String) map.get("jobId");
        String pipelineId = (String) map.get("pipelineId");
        Long checkpointId = map.get("checkpointId") instanceof Number ? ((Number) map.get("checkpointId")).longValue() : null;
        Long triggerTimestamp = map.get("triggerTimestamp") instanceof Number ? ((Number) map.get("triggerTimestamp")).longValue() : null;
        Long completedTimestamp = map.get("completedTimestamp") instanceof Number ? ((Number) map.get("completedTimestamp")).longValue() : null;
        if (jobId == null || pipelineId == null || checkpointId == null
                || triggerTimestamp == null || completedTimestamp == null) {
            LOG.warn("Checkpoint data missing required fields, skipping deserialization");
            return null;
        }
        String checkpointTypeName = (String) map.get("checkpointType");
        CheckpointType checkpointType = checkpointTypeName != null ? CheckpointType.valueOf(checkpointTypeName) : CheckpointType.CHECKPOINT;
        Boolean restored = (Boolean) map.get("restored");

        Map<String, Object> taskStatesMap = (Map<String, Object>) map.get("taskStates");
        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        if (taskStatesMap != null) {
            for (Map.Entry<String, Object> entry : taskStatesMap.entrySet()) {
                TaskLocation taskLocation;
                try {
                    taskLocation = stringToTaskLocation(entry.getKey());
                } catch (Exception e) {
                    taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);
                }
                Map<String, Object> stateMap = (Map<String, Object>) entry.getValue();
                TaskStateSnapshot snapshot = deserializeTaskStateSnapshot(stateMap, taskLocation);
                taskStates.put(taskLocation, snapshot);
            }
        }

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(triggerTimestamp)
                .completedTimestamp(completedTimestamp)
                .checkpointType(checkpointType)
                .taskStates(taskStates)
                .build();

        if (restored != null) {
            checkpoint.setRestored(restored);
        }

        return checkpoint;
    }

    private TaskStateSnapshot deserializeTaskStateSnapshot(Map<String, Object> map, TaskLocation taskLocation) {
        if (map == null) {
            return null;
        }
        TaskStateSnapshot snapshot = new TaskStateSnapshot(taskLocation);

        Map<String, Object> operatorStates = (Map<String, Object>) map.get("operatorStates");
        if (operatorStates != null) {
            for (Map.Entry<String, Object> entry : operatorStates.entrySet()) {
                snapshot.putOperatorState(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> keyedStates = (Map<String, Object>) map.get("keyedStates");
        if (keyedStates != null) {
            for (Map.Entry<String, Object> entry : keyedStates.entrySet()) {
                snapshot.putKeyedState(entry.getKey(), entry.getValue());
            }
        }

        return snapshot;
    }

    @Override
    public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws Exception {
        ensureEpochTable();
        byte[] stateData = serializeEpochManifest(manifest);
        long sid = nextSid();

        SQL sql = SQL.begin().name("storeEpochManifest").querySpace(querySpace)
                .sql("INSERT INTO " + EPOCH_TABLE_NAME +
                        " (sid, job_id, pipeline_id, epoch_id, checkpoint_type, state, timestamp, state_data) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                        sid,
                        manifest.getJobId(),
                        manifest.getPipelineId(),
                        manifest.getEpochId(),
                        manifest.getCheckpointType() != null ? manifest.getCheckpointType().name() : "CHECKPOINT",
                        manifest.getState() != null ? manifest.getState().name() : "COMMITTED",
                        manifest.getTimestamp(),
                        stateData)
                .end();

        jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
            jdbcTemplate.executeUpdate(sql);
            return null;
        });

        LOG.debug("Stored epoch manifest {} for job {}/{}", manifest.getEpochId(), jobId, pipelineId);
    }

    @Override
    public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws Exception {
        if (!epochTableExists()) {
            return null;
        }

        SQL sql = SQL.begin().name("loadLatestEpochManifest").querySpace(querySpace)
                .sql("SELECT state_data FROM " + EPOCH_TABLE_NAME +
                        " WHERE job_id = ? AND pipeline_id = ?" +
                        " ORDER BY epoch_id DESC LIMIT 1", jobId, pipelineId)
                .end();

        byte[][] result = {null};
        jdbcTemplate.executeQuery(sql, dataSet -> {
            for (IDataRow row : dataSet) {
                result[0] = row.getBytes(0);
                break;
            }
            return null;
        });
        return deserializeEpochManifest(result[0]);
    }

    private boolean epochTableExists() {
        try {
            return jdbcTemplate.existsTable(querySpace, EPOCH_TABLE_NAME);
        } catch (Exception e) {
            LOG.debug("Failed to check epoch table existence", e);
            return false;
        }
    }

    private void ensureEpochTable() {
        if (epochTableInitialized) {
            return;
        }
        synchronized (this) {
            if (epochTableInitialized) {
                return;
            }

            if (epochTableExists()) {
                epochTableInitialized = true;
                return;
            }

            String ddl = "CREATE TABLE " + EPOCH_TABLE_NAME + " (" +
                    "sid BIGINT NOT NULL, " +
                    "job_id VARCHAR(255) NOT NULL, " +
                    "pipeline_id VARCHAR(255) NOT NULL, " +
                    "epoch_id BIGINT NOT NULL, " +
                    "checkpoint_type VARCHAR(50) NOT NULL, " +
                    "state VARCHAR(50) NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "state_data BLOB, " +
                    "created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (sid)" +
                    ")";

            SQL createTableSql = SQL.begin().name("createEpochManifestTable").querySpace(querySpace)
                    .sql(ddl)
                    .end();

            jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                jdbcTemplate.executeUpdate(createTableSql);

                try {
                    SQL idx1 = SQL.begin().name("createEpochJobPipelineIdx").querySpace(querySpace)
                            .sql("CREATE INDEX idx_epoch_job_pipeline ON " + EPOCH_TABLE_NAME + " (job_id, pipeline_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx1);
                } catch (Exception e) {
                    LOG.debug("Index idx_epoch_job_pipeline may already exist, ignoring", e);
                }

                try {
                    SQL idx2 = SQL.begin().name("createEpochIdIdx").querySpace(querySpace)
                            .sql("CREATE INDEX idx_epoch_id ON " + EPOCH_TABLE_NAME + " (epoch_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx2);
                } catch (Exception e) {
                    LOG.debug("Index idx_epoch_id may already exist, ignoring", e);
                }

                return null;
            });
            epochTableInitialized = true;
        }
    }

    private byte[] serializeEpochManifest(EpochManifest manifest) {
        Map<String, Object> serializable = new LinkedHashMap<>();
        serializable.put("epochId", manifest.getEpochId());
        serializable.put("jobId", manifest.getJobId());
        serializable.put("pipelineId", manifest.getPipelineId());
        serializable.put("timestamp", manifest.getTimestamp());
        if (manifest.getCheckpointType() != null) {
            serializable.put("checkpointType", manifest.getCheckpointType().name());
        }
        if (manifest.getState() != null) {
            serializable.put("state", manifest.getState().name());
        }

        Map<String, Object> taskSnapshotsMap = new LinkedHashMap<>();
        for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : manifest.getTaskSnapshots().entrySet()) {
            String key = taskLocationToString(entry.getKey());
            Map<String, Object> snapshotMap = new LinkedHashMap<>();
            if (entry.getValue().getOperatorStates() != null && !entry.getValue().getOperatorStates().isEmpty()) {
                snapshotMap.put("operatorStates", entry.getValue().getOperatorStates());
            }
            if (entry.getValue().getKeyedStates() != null && !entry.getValue().getKeyedStates().isEmpty()) {
                snapshotMap.put("keyedStates", entry.getValue().getKeyedStates());
            }
            taskSnapshotsMap.put(key, snapshotMap);
        }
        serializable.put("taskSnapshots", taskSnapshotsMap);

        if (manifest.getStreamModelFingerprint() != null) {
            StreamModelFingerprint fp = manifest.getStreamModelFingerprint();
            Map<String, Object> fpMap = new LinkedHashMap<>();
            fpMap.put("version", fp.getVersion());
            fpMap.put("dagTopologyHash", fp.getDagTopologyHash());
            fpMap.put("requirementsHash", fp.getRequirementsHash());
            fpMap.put("checkpointParticipantsHash", fp.getCheckpointParticipantsHash());
            fpMap.put("componentHashes", fp.getComponentHashes());
            serializable.put("streamModelFingerprint", fpMap);
        }

        if (manifest.getSegments() != null && !manifest.getSegments().isEmpty()) {
            java.util.List<Map<String, Object>> segmentsList = new java.util.ArrayList<>();
            for (StateSegmentDescriptor seg : manifest.getSegments()) {
                Map<String, Object> segMap = new LinkedHashMap<>();
                segMap.put("segmentType", seg.getSegmentType());
                segMap.put("path", seg.getPath());
                segMap.put("codec", seg.getCodec());
                segMap.put("checksum", seg.getChecksum());
                segMap.put("schemaVersion", seg.getSchemaVersion());
                segmentsList.add(segMap);
            }
            serializable.put("segments", segmentsList);
        }

        return JsonTool.serialize(serializable, false).getBytes(StandardCharsets.UTF_8);
    }

    private EpochManifest deserializeEpochManifest(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        Map<String, Object> map = JsonTool.parseMap(json);
        if (map == null) {
            return null;
        }

        long epochId = map.get("epochId") instanceof Number ? ((Number) map.get("epochId")).longValue() : -1;
        String jobId = (String) map.get("jobId");
        String pipelineId = (String) map.get("pipelineId");
        long timestamp = map.get("timestamp") instanceof Number ? ((Number) map.get("timestamp")).longValue() : 0;

        String checkpointTypeName = (String) map.get("checkpointType");
        CheckpointType checkpointType = checkpointTypeName != null ? CheckpointType.valueOf(checkpointTypeName) : null;

        String stateName = (String) map.get("state");
        EpochState epochState = stateName != null ? EpochState.valueOf(stateName) : null;

        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        Map<String, Object> taskSnapshotsMap = (Map<String, Object>) map.get("taskSnapshots");
        if (taskSnapshotsMap != null) {
            for (Map.Entry<String, Object> entry : taskSnapshotsMap.entrySet()) {
                TaskLocation taskLocation;
                try {
                    taskLocation = stringToTaskLocation(entry.getKey());
                } catch (Exception e) {
                    taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);
                }
                Map<String, Object> stateMap = (Map<String, Object>) entry.getValue();
                TaskStateSnapshot snapshot = deserializeTaskStateSnapshot(stateMap, taskLocation);
                taskSnapshots.put(taskLocation, snapshot);
            }
        }

        StreamModelFingerprint fingerprint = null;
        Map<String, Object> fpMap = (Map<String, Object>) map.get("streamModelFingerprint");
        if (fpMap != null) {
            StreamModelFingerprint.Builder fpBuilder = StreamModelFingerprint.builder();
            fpBuilder.version((String) fpMap.get("version"));
            fpBuilder.dagTopologyHash((String) fpMap.get("dagTopologyHash"));
            fpBuilder.requirementsHash((String) fpMap.get("requirementsHash"));
            fpBuilder.checkpointParticipantsHash((String) fpMap.get("checkpointParticipantsHash"));
            Map<String, String> compHashes = (Map<String, String>) fpMap.get("componentHashes");
            if (compHashes != null) {
                for (Map.Entry<String, String> e : compHashes.entrySet()) {
                    fpBuilder.addComponentHash(e.getKey(), e.getValue());
                }
            }
            fingerprint = fpBuilder.build();
        }

        java.util.List<StateSegmentDescriptor> segments = new java.util.ArrayList<>();
        java.util.List<Map<String, Object>> segmentsList = (java.util.List<Map<String, Object>>) map.get("segments");
        if (segmentsList != null) {
            for (Map<String, Object> segMap : segmentsList) {
                segments.add(new StateSegmentDescriptor(
                        (String) segMap.get("segmentType"),
                        (String) segMap.get("path"),
                        (String) segMap.get("codec"),
                        (String) segMap.get("checksum"),
                        segMap.get("schemaVersion") instanceof Number ? ((Number) segMap.get("schemaVersion")).intValue() : 1
                ));
            }
        }

        return new EpochManifest(epochId, jobId, pipelineId, timestamp, checkpointType, epochState,
                taskSnapshots, fingerprint, segments);
    }

    private static synchronized long nextSid() {
        return ++sidSequence;
    }
}
