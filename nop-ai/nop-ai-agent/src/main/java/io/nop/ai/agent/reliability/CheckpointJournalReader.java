package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses a per-session {@code journal.md} file (written by
 * {@link CheckpointJournalWriter}) back into a list of {@link Checkpoint}
 * objects, ordered by their appearance in the file (ascending seq).
 *
 * <p>Supports two read modes:
 * <ul>
 *   <li>{@link #readAll} — full scan, returns every checkpoint in the file
 *       (used when no snapshot is available — the degraded recovery path).</li>
 *   <li>{@link #readAfter} — incremental scan, returns only checkpoints whose
 *       watermark appears strictly after the given {@code lastWatermark} in
 *       file order (used by the snapshot-accelerated recovery path to replay
 *       only the entries created after the snapshot point).</li>
 * </ul>
 *
 * <p><b>Corrupted-entry handling</b> (Minimum Rules #24 — no silent skip): a
 * section that is missing a required field or has an unparseable value is
 * skipped with a {@link Level#WARNING} log entry naming the section header and
 * the parse error. The remaining sections are still parsed. An empty or
 * non-existent file returns an empty list (legitimate semantics, declared in
 * Javadoc) — not an exception.
 */
public final class CheckpointJournalReader {

    private static final Logger LOG = Logger.getLogger(CheckpointJournalReader.class.getName());

    private static final String SECTION_DELIMITER = "## CP-";

    /**
     * Read all checkpoints from the journal file, in file order.
     *
     * @param journalFile the per-session journal.md path; never null
     * @return an unmodifiable, possibly-empty list of checkpoints ordered by
     *         ascending seq; never null
     * @throws NopAiAgentException if the file exists but cannot be read
     */
    public List<Checkpoint> readAll(Path journalFile) {
        if (journalFile == null) {
            throw new NopAiAgentException("CheckpointJournalReader.readAll: journalFile must not be null");
        }
        String content = readTextIfExists(journalFile);
        if (content == null) {
            return Collections.emptyList();
        }
        return parseAll(content);
    }

    /**
     * Read only the checkpoints that appear strictly after the checkpoint with
     * the given watermark in the journal file. Used by the snapshot-accelerated
     * recovery path: the snapshot captures the state at {@code lastWatermark},
     * so only the incremental entries after it need to be replayed.
     *
     * <p>If {@code lastWatermark} is not found in the file, the full list is
     * returned (degraded-but-correct fallback — no checkpoint is silently
     * dropped).
     *
     * @param journalFile    the per-session journal.md path; never null
     * @param lastWatermark  the watermark of the snapshot point; never null
     * @return an unmodifiable, possibly-empty list of the checkpoints after
     *         {@code lastWatermark}, in file order; never null
     * @throws NopAiAgentException if the file exists but cannot be read
     */
    public List<Checkpoint> readAfter(Path journalFile, String lastWatermark) {
        if (journalFile == null) {
            throw new NopAiAgentException("CheckpointJournalReader.readAfter: journalFile must not be null");
        }
        if (lastWatermark == null) {
            throw new NopAiAgentException("CheckpointJournalReader.readAfter: lastWatermark must not be null");
        }
        String content = readTextIfExists(journalFile);
        if (content == null) {
            return Collections.emptyList();
        }
        List<Checkpoint> all = parseAll(content);
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (lastWatermark.equals(all.get(i).getWatermark())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return Collections.unmodifiableList(all);
        }
        return Collections.unmodifiableList(all.subList(idx + 1, all.size()));
    }

    private String readTextIfExists(Path journalFile) {
        if (!Files.exists(journalFile)) {
            return null;
        }
        try {
            return Files.readString(journalFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopAiAgentException(
                    "CheckpointJournalReader: failed to read " + journalFile + ": " + e.getMessage(), e);
        }
    }

    private List<Checkpoint> parseAll(String content) {
        int headerEnd = content.indexOf(SECTION_DELIMITER);
        if (headerEnd < 0) {
            return Collections.emptyList();
        }
        String body = content.substring(headerEnd);
        String[] sections = body.split("(?=" + java.util.regex.Pattern.quote(SECTION_DELIMITER) + ")");
        List<Checkpoint> result = new ArrayList<>();
        for (String section : sections) {
            if (!section.startsWith(SECTION_DELIMITER)) {
                continue;
            }
            String header = firstLine(section);
            try {
                Checkpoint cp = parseSection(section);
                if (cp != null) {
                    result.add(cp);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "CheckpointJournalReader: skipping corrupted journal section '" + header + "'",
                        e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private Checkpoint parseSection(String section) {
        String[] lines = section.split("\n", -1);
        if (lines.length == 0) {
            return null;
        }
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(": ");
            if (colon < 0) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 2);
            fields.put(key, value);
        }

        String typeStr = requireField(fields, "type", section);
        String seqStr = requireField(fields, "seq", section);
        String tsStr = requireField(fields, "timestamp", section);
        String watermark = decodeString(requireField(fields, "watermark", section));
        if (watermark == null) {
            throw new NopAiAgentException("CheckpointJournalReader: watermark must not be null in section: "
                    + firstLine(section));
        }
        String mcStr = requireField(fields, "messageCount", section);
        String teStr = requireField(fields, "tokenEstimate", section);

        CheckpointType type;
        try {
            type = CheckpointType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "CheckpointJournalReader: unknown CheckpointType '" + typeStr + "' in section: "
                            + firstLine(section));
        }

        int seq;
        long timestamp;
        int messageCount;
        long tokenEstimate;
        try {
            seq = Integer.parseInt(seqStr.trim());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(
                    "CheckpointJournalReader: invalid seq '" + seqStr + "' in section: " + firstLine(section));
        }
        try {
            timestamp = Instant.parse(tsStr.trim()).toEpochMilli();
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "CheckpointJournalReader: invalid timestamp '" + tsStr + "' in section: " + firstLine(section));
        }
        try {
            messageCount = Integer.parseInt(mcStr.trim());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(
                    "CheckpointJournalReader: invalid messageCount '" + mcStr + "' in section: " + firstLine(section));
        }
        try {
            tokenEstimate = Long.parseLong(teStr.trim());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(
                    "CheckpointJournalReader: invalid tokenEstimate '" + teStr + "' in section: " + firstLine(section));
        }

        String sessionId = decodeString(fields.get("sessionId"));
        String toolName = decodeString(fields.get("toolName"));
        String callId = decodeString(fields.get("callId"));
        String inputSummary = decodeString(fields.get("inputSummary"));
        String outputSummary = decodeString(fields.get("outputSummary"));

        return Checkpoint.of(sessionId, watermark, seq, timestamp, type,
                toolName, callId, inputSummary, outputSummary, messageCount, tokenEstimate);
    }

    private static String requireField(java.util.Map<String, String> fields, String key, String section) {
        String value = fields.get(key);
        if (value == null) {
            throw new NopAiAgentException(
                    "CheckpointJournalReader: missing required field '" + key + "' in section: "
                            + firstLine(section));
        }
        return value;
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text.trim() : text.substring(0, nl).trim();
    }

    /**
     * Decode a journal field value back to a Java string. The value
     * {@code null} (literal token) maps to Java {@code null}; a JSON-quoted
     * string is unescaped via {@link io.nop.core.lang.json.JsonTool}; any other
     * bare token is returned as-is (forward compatibility).
     */
    static String decodeString(String encoded) {
        if (encoded == null || "null".equals(encoded)) {
            return null;
        }
        if (encoded.startsWith("\"")) {
            try {
                Object parsed = io.nop.core.lang.json.JsonTool.parseNonStrict(encoded);
                return parsed == null ? null : parsed.toString();
            } catch (Exception e) {
                return encoded;
            }
        }
        return encoded;
    }
}
