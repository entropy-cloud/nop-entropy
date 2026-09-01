/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.exceptions.StreamException;

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
public final class FileSource implements Source<String, FileSplit, FileSplitEnumeratorState> {

    private static final long serialVersionUID = 1L;

    private final String directoryPath;

    public FileSource(String directoryPath) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "directoryPath")
                    .param(ARG_DETAIL, "directory path must not be null or empty");
        }
        this.directoryPath = directoryPath;
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    @Override
    public SplitEnumerator<FileSplit, FileSplitEnumeratorState> createEnumerator() {
        return new FileSplitEnumerator(directoryPath);
    }

    @Override
    public SplitEnumerator<FileSplit, FileSplitEnumeratorState> restoreEnumerator(
            FileSplitEnumeratorState checkpointState) {
        FileSplitEnumerator enumerator = new FileSplitEnumerator(directoryPath);
        // The actual state restore happens via SplitEnumerator.restoreState() at start();
        // the runtime calls start() after restoreState().
        return enumerator;
    }

    @Override
    public SourceReader<String, FileSplit> createReader(SourceReaderContext readerContext) {
        return new FileSourceReader(readerContext);
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
     * Serializes {@link FileSplit} as a flat UTF-8 string with {@code |} separators:
     * {@code filePath|startOffset|endOffset|currentOffset}. Versioned at 1.
     */
    static final class FileSplitSerializer implements SimpleVersionedSerializer<FileSplit> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(FileSplit obj) throws IOException {
            String filePath = obj.getFilePath();
            if (filePath.indexOf('|') >= 0 || filePath.indexOf('\n') >= 0 || filePath.indexOf('\r') >= 0) {
                throw new IOException("FileSplit filePath contains reserved separator characters "
                        + "('|' / newline): " + filePath);
            }
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
            sb.append(obj.getDirectoryPath() == null ? "" : obj.getDirectoryPath()).append('\n');
            sb.append(obj.getNextSubtaskIndex()).append('\n');
            appendCsvSection(sb, obj.getDiscoveredFiles());
            appendCsvSection(sb, obj.getAssignedFiles());
            appendCsvSection(sb, obj.getFinishedFiles());
            // splitById section: one entry per non-finished split, as
            // "filePath|startOffset|endOffset|currentOffset"
            for (java.util.Map.Entry<String, FileSplit> entry : obj.getSplitById().entrySet()) {
                FileSplit s = entry.getValue();
                sb.append(s.getFilePath()).append('|')
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
                if (path.indexOf(',') >= 0 || path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0) {
                    throw new IOException("Enumerator state file path contains reserved separator "
                            + "characters (',' / newline): " + path);
                }
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
}
