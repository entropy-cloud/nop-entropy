package io.nop.ai.agent.reliability;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.core.lang.json.JsonTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * Parses a per-session {@code snapshot.json} file (written by
 * {@link CheckpointSnapshotWriter}) back into a {@link CheckpointSnapshot}.
 *
 * <p><b>Missing-file semantics</b> (declared in Javadoc): if the file does not
 * exist, {@link #readIfExists} returns {@code null} (legitimate — no snapshot
 * has been generated yet; the recovery path falls back to a full journal scan).
 * This is not an error and not a silent skip — it is an explicit, documented
 * null return.
 */

/** * <p><b>store-layer 边界（审计 AI-2/AI-19 裁定）</b>：自建表/本地文件为引擎内部运行时状态，
 * 保留独立 store 层（不注册 ORM）；租户/软删不适用理由与表清单见
 * {@code ai-dev/design/nop-ai-agent/store-layer-contract.md}。
 */
public final class CheckpointSnapshotReader {

    /**
     * Read the snapshot file if it exists.
     *
     * @param snapshotFile the per-session snapshot.json path; never null
     * @return the parsed snapshot, or {@code null} if the file does not exist
     * @throws NopAiAgentException if the file exists but cannot be parsed
     */
    public CheckpointSnapshot readIfExists(Path snapshotFile) {
        if (snapshotFile == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointSnapshotReader.readIfExists: snapshotFile must not be null");
        }
        if (!Files.exists(snapshotFile)) {
            return null;
        }
        String json;
        try {
            json = Files.readString(snapshotFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "CheckpointSnapshotReader: failed to read " + snapshotFile + ": " + e.getMessage());
        }
        return deserialize(json);
    }

    static CheckpointSnapshot deserialize(String json) {
        Object parsed;
        try {
            parsed = JsonTool.parseNonStrict(json);
        } catch (Exception e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "CheckpointSnapshotReader: failed to parse JSON: " + e.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointSnapshotReader: expected JSON object, got: "
                            + (parsed == null ? "null" : parsed.getClass().getName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;

        String snapshotId = getRequiredString(map, CheckpointSnapshotWriter.FIELD_SNAPSHOT_ID);
        String sessionId = getString(map, CheckpointSnapshotWriter.FIELD_SESSION_ID);
        String lastWatermark = getRequiredString(map, CheckpointSnapshotWriter.FIELD_LAST_WATERMARK);
        int messageCount = getInt(map, CheckpointSnapshotWriter.FIELD_MESSAGE_COUNT);
        long tokenEstimate = getLong(map, CheckpointSnapshotWriter.FIELD_TOKEN_ESTIMATE);
        long createdAtEpochMillis;
        String createdAt = getRequiredString(map, CheckpointSnapshotWriter.FIELD_CREATED_AT);
        try {
            createdAtEpochMillis = Instant.parse(createdAt).toEpochMilli();
        } catch (Exception e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "CheckpointSnapshotReader: invalid createdAt '" + createdAt + "': " + e.getMessage());
        }

        return CheckpointSnapshot.of(snapshotId, sessionId, lastWatermark,
                messageCount, tokenEstimate, createdAtEpochMillis);
    }

    private static String getRequiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointSnapshotReader: missing required field '" + key + "'");
        }
        return value.toString();
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointSnapshotReader: missing required field '" + key + "'");
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointSnapshotReader: invalid int '" + value + "' for field '" + key + "'");
        }
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointSnapshotReader: invalid long '" + value + "' for field '" + key + "'");
        }
    }
}
