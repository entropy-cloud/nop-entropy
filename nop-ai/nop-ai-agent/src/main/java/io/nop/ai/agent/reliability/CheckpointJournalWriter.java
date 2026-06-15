package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Serializes a {@link Checkpoint} to a §5.4a markdown section and appends it to
 * a per-session {@code journal.md} file. The journal is the append-only source
 * of truth for persisted checkpoints — {@code saveCheckpoint} only appends a
 * new {@code ## CP-NNN} section, never rewrites existing content.
 *
 * <p><b>Format</b> (per design §5.4a): the file begins with a
 * {@code # Checkpoint Journal - {sessionId}} header, followed by one
 * {@code ## CP-NNN} section per checkpoint. Each section contains key:value
 * lines for type / seq / timestamp (ISO-8601) / sessionId / watermark /
 * toolName / callId / inputSummary / outputSummary / messageCount /
 * tokenEstimate. String values are JSON-quoted (so multi-line summaries are
 * escaped to a single line); {@code null} strings are written as the literal
 * token {@code null}.
 *
 * <p><b>Thread safety</b>: append writes are synchronized on a per-file lock so
 * concurrent {@code saveCheckpoint} calls for the same session serialize
 * correctly. Cross-session writes target independent files and do not contend.
 *
 * <p><b>Append-only semantics</b>: if the file does not exist it is created
 * with the header. Existing content is never overwritten — every write uses
 * {@link StandardOpenOption#APPEND}.
 */
public final class CheckpointJournalWriter {

    private static final String HEADER_PREFIX = "# Checkpoint Journal - ";

    private final Object ioLock = new Object();

    /**
     * Append a single checkpoint as a {@code ## CP-NNN} section. Creates the
     * file (with the journal header) on first write.
     *
     * @param journalFile the per-session journal.md path; never null
     * @param sessionId   the session id used in the file header; may be null
     *                    (header becomes {@code # Checkpoint Journal - anonymous})
     * @param checkpoint  the checkpoint to serialize; never null
     * @throws NopAiAgentException if an I/O error occurs
     */
    public void appendCheckpoint(Path journalFile, String sessionId, Checkpoint checkpoint) {
        if (journalFile == null) {
            throw new NopAiAgentException("CheckpointJournalWriter.appendCheckpoint: journalFile must not be null");
        }
        if (checkpoint == null) {
            throw new NopAiAgentException("CheckpointJournalWriter.appendCheckpoint: checkpoint must not be null");
        }

        String section = serializeSection(checkpoint);

        synchronized (ioLock) {
            try {
                ensureFileWithHeader(journalFile, sessionId);
                Files.write(journalFile, section.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new NopAiAgentException(
                        "CheckpointJournalWriter.appendCheckpoint: failed to append to " + journalFile
                                + ": " + e.getMessage(), e);
            }
        }
    }

    private void ensureFileWithHeader(Path journalFile, String sessionId) throws IOException {
        if (!Files.exists(journalFile)) {
            Path parent = journalFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String header = HEADER_PREFIX + (sessionId != null ? sessionId : "anonymous") + "\n\n";
            Files.write(journalFile, header.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
    }

    static String serializeSection(Checkpoint cp) {
        StringBuilder sb = new StringBuilder();
        sb.append("## CP-").append(formatSeq(cp.getSeq())).append('\n');
        sb.append("type: ").append(cp.getType().name()).append('\n');
        sb.append("seq: ").append(cp.getSeq()).append('\n');
        sb.append("timestamp: ").append(Instant.ofEpochMilli(cp.getTimestamp()).toString()).append('\n');
        sb.append("sessionId: ").append(encodeString(cp.getSessionId())).append('\n');
        sb.append("watermark: ").append(encodeString(cp.getWatermark())).append('\n');
        sb.append("toolName: ").append(encodeString(cp.getToolName())).append('\n');
        sb.append("callId: ").append(encodeString(cp.getCallId())).append('\n');
        sb.append("inputSummary: ").append(encodeString(cp.getInputSummary())).append('\n');
        sb.append("outputSummary: ").append(encodeString(cp.getOutputSummary())).append('\n');
        sb.append("messageCount: ").append(cp.getMessageCount()).append('\n');
        sb.append("tokenEstimate: ").append(cp.getTokenEstimate()).append('\n');
        sb.append('\n');
        return sb.toString();
    }

    private static String formatSeq(int seq) {
        return String.format("%03d", seq);
    }

    /**
     * JSON-encode a string value so multi-line content is escaped to a single
     * line. {@code null} is rendered as the literal token {@code null}.
     */
    static String encodeString(String value) {
        if (value == null) {
            return "null";
        }
        return io.nop.core.lang.json.JsonTool.stringify(value);
    }
}
