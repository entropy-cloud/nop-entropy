package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.core.lang.json.JsonTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes a {@link CheckpointSnapshot} to a per-session {@code snapshot.json}
 * file (design §5.4a). The snapshot is a derived cache — it records the
 * {@code lastWatermark} and context-size aggregate at a known point so that
 * recovery can skip replaying the entire journal.
 *
 * <p>The JSON format follows §5.4a (recovery-critical field subset):
 * <pre>{@code
 * {
 *   "snapshotId": "snap-001",
 *   "sessionId": "sess-001",
 *   "lastWatermark": "wm-1",
 *   "messageCount": 3,
 *   "tokenEstimate": 42,
 *   "createdAt": "2026-06-15T10:00:00.000Z"
 * }
 * }</pre>
 *
 * <p><b>Overwrite semantics</b>: unlike the append-only journal, the snapshot
 * file is overwritten on each write (it always represents the latest aggregate
 * state). Writes are synchronized on a per-instance lock.
 */
public final class CheckpointSnapshotWriter {

    static final String FIELD_SNAPSHOT_ID = "snapshotId";
    static final String FIELD_SESSION_ID = "sessionId";
    static final String FIELD_LAST_WATERMARK = "lastWatermark";
    static final String FIELD_MESSAGE_COUNT = "messageCount";
    static final String FIELD_TOKEN_ESTIMATE = "tokenEstimate";
    static final String FIELD_CREATED_AT = "createdAt";

    private final Object ioLock = new Object();

    /**
     * Write (or overwrite) the snapshot file.
     *
     * @param snapshotFile the per-session snapshot.json path; never null
     * @param snapshot     the snapshot to serialize; never null
     * @throws NopAiAgentException if serialization or I/O fails
     */
    public void write(Path snapshotFile, CheckpointSnapshot snapshot) {
        if (snapshotFile == null) {
            throw new NopAiAgentException("CheckpointSnapshotWriter.write: snapshotFile must not be null");
        }
        if (snapshot == null) {
            throw new NopAiAgentException("CheckpointSnapshotWriter.write: snapshot must not be null");
        }

        String json = serialize(snapshot);

        synchronized (ioLock) {
            try {
                Path parent = snapshotFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(snapshotFile, json.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new NopAiAgentException(
                        "CheckpointSnapshotWriter.write: failed to write " + snapshotFile
                                + ": " + e.getMessage(), e);
            }
        }
    }

    static String serialize(CheckpointSnapshot snap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(FIELD_SNAPSHOT_ID, snap.getSnapshotId());
        map.put(FIELD_SESSION_ID, snap.getSessionId());
        map.put(FIELD_LAST_WATERMARK, snap.getLastWatermark());
        map.put(FIELD_MESSAGE_COUNT, snap.getMessageCount());
        map.put(FIELD_TOKEN_ESTIMATE, snap.getTokenEstimate());
        map.put(FIELD_CREATED_AT, snap.getCreatedAtIso());
        try {
            return JsonTool.stringify(map);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "CheckpointSnapshotWriter.serialize: failed to serialize snapshot: " + e.getMessage(), e);
        }
    }
}
