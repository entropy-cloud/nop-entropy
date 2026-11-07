/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.connector.SampleableSource;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitEnumerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Stage 49 Phase 3: bounded {@link Source} that emits text-file lines as {@code String}
 * records. The reference split-based source proving the FLIP-27 path end-to-end:
 *
 * <ul>
 *   <li>{@code createEnumerator} → {@link FileSplitEnumerator} scans a directory, splits
 *       are one-per-file (v1: no sub-file chunking), assigned round-robin to subtasks on
 *       registration / pull request;</li>
 *   <li>{@code createReader} → {@link FileSourceReader} reads each assigned file line by
 *       line, tracks per-split cursor (byte offset) for checkpoint/restore;</li>
 *   <li>{@link Boundedness#BOUNDED} — the job terminates once all splits are consumed.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * FileSource source = new FileSource("/path/to/input/dir");
 * env.addSource(source, "file-source").map(...).addSink(...);
 * env.execute("file-pipeline");
 * }</pre>
 *
 * <p>The source is serializable (carries only the directory path); the live enumerator /
 * reader instances are created per-execution by the runtime.
 */
public final class FileSource implements Source<String, FileSplit, FileSplitEnumeratorState>, SampleableSource {

    private static final long serialVersionUID = 1L;

    private final String directoryPath;

    /**
     * R5-CON-07: per-line aggregation bound handed to every created
     * {@link FileSourceReader} (see {@link FileSourceReader#DEFAULT_MAX_LINE_BYTES}).
     */
    private final long maxLineBytes;

    /**
     * WI5 (plan 2330): emission rate caps (records/second, bytes/second). 0 = channel
     * off (unlimited) — the UNSET config value normalizes to 0 at the factory; an
     * explicitly negative value never reaches this constructor (the factory rejects
     * it, since unset vs explicit-zero is indistinguishable here).
     */
    private final long rateLimitRowsPerSecond;
    private final long rateLimitBytesPerSecond;

    /**
     * WI5 (plan 2330): split assignment policy — {@code round-robin} (default) or
     * {@code size-aware} (see {@link FileSplitEnumerator}); any other value is a typed
     * construction error, never a silent fallback.
     */
    private final String splitAssignmentPolicy;

    public FileSource(String directoryPath) {
        this(directoryPath, FileSourceReader.DEFAULT_MAX_LINE_BYTES, 0L, 0L,
                FileSplitEnumerator.POLICY_ROUND_ROBIN);
    }

    /**
     * @param maxLineBytes fail-fast bound for a single input line
     *                     (R5-CON-07); must be &gt; 0.
     */
    public FileSource(String directoryPath, long maxLineBytes) {
        this(directoryPath, maxLineBytes, 0L, 0L, FileSplitEnumerator.POLICY_ROUND_ROBIN);
    }

    /**
     * WI5 full constructor: rate caps + split assignment policy. Both enumerator
     * construction paths (fresh and restore) receive the policy, so the behavior
     * survives checkpoint/restore.
     */
    public FileSource(String directoryPath, long maxLineBytes, long rateLimitRowsPerSecond,
                      long rateLimitBytesPerSecond, String splitAssignmentPolicy) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "directoryPath")
                    .param(ARG_DETAIL, "directory path must not be null or empty");
        }
        if (maxLineBytes <= 0) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "maxLineBytes")
                    .param(ARG_DETAIL, "maxLineBytes must be positive: " + maxLineBytes);
        }
        if (rateLimitRowsPerSecond < 0) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "rateLimitRowsPerSecond")
                    .param(ARG_DETAIL, "rateLimitRowsPerSecond must not be negative (0 = unlimited): "
                            + rateLimitRowsPerSecond);
        }
        if (rateLimitBytesPerSecond < 0) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "rateLimitBytesPerSecond")
                    .param(ARG_DETAIL, "rateLimitBytesPerSecond must not be negative (0 = unlimited): "
                            + rateLimitBytesPerSecond);
        }
        if (!FileSplitEnumerator.isValidPolicy(splitAssignmentPolicy)) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "splitAssignmentPolicy")
                    .param(ARG_DETAIL, "unknown split assignment policy: " + splitAssignmentPolicy
                            + " (expected " + FileSplitEnumerator.POLICY_ROUND_ROBIN + " or "
                            + FileSplitEnumerator.POLICY_SIZE_AWARE + ")");
        }
        this.directoryPath = directoryPath;
        this.maxLineBytes = maxLineBytes;
        this.rateLimitRowsPerSecond = rateLimitRowsPerSecond;
        this.rateLimitBytesPerSecond = rateLimitBytesPerSecond;
        this.splitAssignmentPolicy = splitAssignmentPolicy;
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    public long getMaxLineBytes() {
        return maxLineBytes;
    }

    @Override
    public SplitEnumerator<FileSplit, FileSplitEnumeratorState> createEnumerator() {
        return new FileSplitEnumerator(directoryPath, splitAssignmentPolicy);
    }

    @Override
    public SplitEnumerator<FileSplit, FileSplitEnumeratorState> restoreEnumerator(
            FileSplitEnumeratorState checkpointState) {
        FileSplitEnumerator enumerator = new FileSplitEnumerator(directoryPath, splitAssignmentPolicy);
        // The actual state restore happens via SplitEnumerator.restoreState() at start();
        // the runtime calls start() after restoreState().
        return enumerator;
    }

    /** WI5: configured rows/second rate cap (0 = unlimited). */
    public long getRateLimitRowsPerSecond() {
        return rateLimitRowsPerSecond;
    }

    /** WI5: configured bytes/second rate cap (0 = unlimited). */
    public long getRateLimitBytesPerSecond() {
        return rateLimitBytesPerSecond;
    }

    /** WI5: split assignment policy ({@code round-robin} | {@code size-aware}). */
    public String getSplitAssignmentPolicy() {
        return splitAssignmentPolicy;
    }

    @Override
    public SourceReader<String, FileSplit> createReader(SourceReaderContext readerContext) {
        // R5-CON-10: the source directory flows into the reader so openSplit can
        // enforce split-path containment (legacy checkpoints carry no checksum).
        return new FileSourceReader(readerContext, directoryPath, maxLineBytes,
                rateLimitRowsPerSecond, rateLimitBytesPerSecond);
    }

    @Override
    public SimpleVersionedSerializer<FileSplitEnumeratorState> getEnumeratorStateSerializer() {
        return new FileSplitEnumeratorStateSerializer();
    }

    @Override
    public SimpleVersionedSerializer<FileSplit> getSplitSerializer() {
        return new FileSplitSerializer();
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    // ====================== Serializers ======================

    /**
     * Fail-fast guard for path fields embedded in serialized payloads: the
     * payload format cannot escape its reserved separator characters, so a
     * path carrying one would serialize fine and then corrupt parsing at
     * restore time. {@code charsetDesc} is the human-readable reserved set
     * used verbatim in the error message; {@code context} names the validated
     * field.
     */
    private static void validateReservedChars(String path, String reserved, String charsetDesc,
                                              String context) throws IOException {
        for (int i = 0; i < reserved.length(); i++) {
            if (path.indexOf(reserved.charAt(i)) >= 0) {
                throw new IOException(context + " contains reserved separator characters ("
                        + charsetDesc + "): " + path);
            }
        }
    }

    /**
     * Serializes {@link FileSplit} as a flat UTF-8 string with {@code |} separators:
     * {@code filePath|startOffset|endOffset|currentOffset}. Versioned at 1.
     */
    static final class FileSplitSerializer implements SimpleVersionedSerializer<FileSplit> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(FileSplit obj) throws IOException {
            String filePath = obj.getFilePath();
            validateReservedChars(filePath, "|\n\r", "'|' / newline", "FileSplit filePath");
            String s = obj.getFilePath() + "|" + obj.getStartOffset() + "|"
                    + obj.getEndOffset() + "|" + obj.getCurrentOffset();
            return s.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public FileSplit deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported FileSplit serializer version: " + version);
            }
            String s = new String(bytes, StandardCharsets.UTF_8);
            String[] parts = s.split("\\|", -1);
            if (parts.length != 4) {
                // More than 4 parts means the file path itself contained '|' and the
                // payload is corrupt — fail fast instead of silently mis-parsing offsets.
                throw new IOException("Malformed FileSplit payload: " + s);
            }
            try {
                return new FileSplit(parts[0],
                        Long.parseLong(parts[1]),
                        Long.parseLong(parts[2]),
                        Long.parseLong(parts[3]));
            } catch (NumberFormatException e) {
                throw new IOException("Malformed FileSplit offsets in payload: " + s, e);
            }
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }

    /**
     * Serializes {@link FileSplitEnumeratorState} as a UTF-8 string of newline-separated
     * sections (discovered / assigned / finished / splitById / nextSubtaskIndex). Versioned at 1.
     */
    static final class FileSplitEnumeratorStateSerializer
            implements SimpleVersionedSerializer<FileSplitEnumeratorState> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(FileSplitEnumeratorState obj) throws IOException {
            StringBuilder sb = new StringBuilder();
            // B6' (plan 366): the directory path is the newline-delimited first
            // section, so only line terminators can corrupt it — unlike split
            // paths, '|' and ',' are legal here and must NOT be rejected.
            String directoryPath = obj.getDirectoryPath();
            if (directoryPath != null && (directoryPath.indexOf('\n') >= 0 || directoryPath.indexOf('\r') >= 0)) {
                throw new IOException("Enumerator state directory path contains reserved line-separator "
                        + "characters (newline): " + directoryPath);
            }
            sb.append(directoryPath == null ? "" : directoryPath).append('\n');
            sb.append(obj.getNextSubtaskIndex()).append('\n');
            appendCsvSection(sb, obj.getDiscoveredFiles());
            appendCsvSection(sb, obj.getAssignedFiles());
            appendCsvSection(sb, obj.getFinishedFiles());
            // splitById section: one entry per non-finished split, as
            // "filePath|startOffset|endOffset|currentOffset"
            for (java.util.Map.Entry<String, FileSplit> entry : obj.getSplitById().entrySet()) {
                FileSplit s = entry.getValue();
                // Split paths carry the same reserved set enforced by
                // FileSplitSerializer: the same path text is embedded in both
                // payload formats, so a reserved character must be rejected at
                // serialize time rather than surface as payload corruption at
                // restore time.
                String path = s.getFilePath();
                validateReservedChars(path, "|\n\r", "'|' / newline", "Enumerator state split path");
                sb.append(path).append('|')
                        .append(s.getStartOffset()).append('|')
                        .append(s.getEndOffset()).append('|')
                        .append(s.getCurrentOffset()).append('\n');
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        /**
         * Appends one CSV section. File paths containing ',' or a newline cannot
         * round-trip through the '|' / ',' / newline separated payload and are rejected
         * at serialize time (fail fast instead of silent corruption on restore).
         */
        private static void appendCsvSection(StringBuilder sb, java.util.Set<String> paths)
                throws IOException {
            boolean first = true;
            for (String path : paths) {
                validateReservedChars(path, ",\n\r", "',' / newline", "Enumerator state file path");
                if (!first) {
                    sb.append(',');
                }
                sb.append(path);
                first = false;
            }
            sb.append('\n');
        }

        @Override
        public FileSplitEnumeratorState deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported FileSplitEnumeratorState serializer version: " + version);
            }
            String s = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = s.split("\n", -1);
            if (lines.length < 6) {
                throw new IOException("Malformed FileSplitEnumeratorState payload");
            }
            String directoryPath = lines[0].isEmpty() ? null : lines[0];
            int nextSubtaskIndex;
            try {
                nextSubtaskIndex = Integer.parseInt(lines[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Malformed nextSubtaskIndex: " + lines[1], e);
            }
            java.util.Set<String> discovered = splitToSet(lines[2]);
            java.util.Set<String> assigned = splitToSet(lines[3]);
            java.util.Set<String> finished = splitToSet(lines[4]);
            java.util.Map<String, FileSplit> splitById = new java.util.LinkedHashMap<>();
            for (int i = 5; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length != 4) {
                    // A non-empty split line with a part count other than 4 is payload
                    // corruption (e.g. a path containing '|'). Skipping it would
                    // silently lose a split on restore — fail fast instead.
                    throw new IOException("Malformed split line in enumerator state payload: " + line);
                }
                FileSplit split;
                try {
                    split = new FileSplit(parts[0],
                            Long.parseLong(parts[1]),
                            Long.parseLong(parts[2]),
                            Long.parseLong(parts[3]));
                } catch (NumberFormatException e) {
                    throw new IOException("Malformed offsets in split line: " + line, e);
                }
                splitById.put(split.splitId(), split);
            }
            return new FileSplitEnumeratorState(directoryPath, discovered, assigned, finished, splitById, nextSubtaskIndex);
        }

        private static java.util.Set<String> splitToSet(String csv) {
            java.util.Set<String> result = new java.util.LinkedHashSet<>();
            if (csv == null || csv.isEmpty()) return result;
            for (String p : csv.split(",", -1)) {
                if (!p.isEmpty()) result.add(p);
            }
            return result;
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }
    /**
     * WI4 (plan 2329): read-only data sampling (SampleableSource). Walks the directory
     * with the same discovery rule as {@link FileSplitEnumerator} (Files.walk, regular
     * files, discovery order — no cross-file ordering promise) and reads up to
     * {@code limit} lines. No split is assigned and no cursor state is touched: the
     * sampling path is fully outside the checkpointed reader lifecycle, honoring the
     * H-1 side-effect budget ("read-only scan, no split assignment"). The per-line
     * byte bound matches the reader contract so a sample also surfaces lines that
     * would fail a real job.
     */
    @Override
    public List<Object> sample(int limit) {
        if (limit <= 0) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "limit")
                    .param(ARG_DETAIL, "sample limit must be positive: " + limit);
        }
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "directoryPath")
                    .param(ARG_DETAIL, "Source directory not found or not a directory: " + directoryPath);
        }
        List<Object> records = new ArrayList<>();
        List<Path> files;
        try {
            // CRA 8.44 R4-04 W-09 (C6-011; adjudication C.4): the walk Stream is
            // TWR-owned — sample is ops-callable in a failure loop, and a mid-walk
            // failure (unreadable subdir) leaked one directory fd per call on the
            // bare shape (no finalizer/Cleaner backs UnixDirectoryStream).
            try (Stream<Path> walkStream = Files.walk(dir)) {
                files = walkStream.filter(Files::isRegularFile).collect(Collectors.toList());
            }
            for (Path file : files) {
                if (records.size() >= limit) {
                    break;
                }
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while (records.size() < limit && (line = reader.readLine()) != null) {
                        if (line.getBytes(StandardCharsets.UTF_8).length > maxLineBytes) {
                            throw new StreamException(ERR_STREAM_NULL_ARG)
                                    .param(ARG_ARG_NAME, "maxLineBytes")
                                    .param(ARG_DETAIL, "sampled line exceeds maxLineBytes ("
                                            + maxLineBytes + ") in " + file
                                            + " — the same line would fail a real job");
                        }
                        records.add(line);
                    }
                }
            }
        } catch (IOException e) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "directoryPath")
                    .param(ARG_DETAIL, "Failed to sample files under " + directoryPath + ": " + e);
        }
        return records;
    }

}
