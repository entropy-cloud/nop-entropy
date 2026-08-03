/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.Serializable;
import java.util.Objects;

import io.nop.stream.core.source.SourceSplit;

/**
 * Stage 49 Phase 3: a {@link SourceSplit} backed by a file path + byte range. The
 * enumerator scans a directory and creates one {@code FileSplit} per file (v1: one split
 * per file; future versions can split large files into multiple ranges via the
 * {@code endOffset} field).
 *
 * <p>The cursor carried through checkpoint/restore is the {@code currentOffset} — the next
 * byte position to read on restore. On fresh assignment, {@code currentOffset == startOffset}.
 * On restore, the reader resumes from {@code currentOffset}.
 */
public final class FileSplit implements SourceSplit {

    private static final long serialVersionUID = 1L;

    private final String filePath;
    private final long startOffset;
    private final long endOffset;
    private final long currentOffset;

    public FileSplit(String filePath, long startOffset, long endOffset, long currentOffset) {
        this.filePath = filePath;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.currentOffset = currentOffset;
    }

    /** Fresh split: cursor starts at startOffset. */
    public FileSplit(String filePath, long startOffset, long endOffset) {
        this(filePath, startOffset, endOffset, startOffset);
    }

    /** Whole-file split from 0 to file length (caller passes file size). */
    public FileSplit(String filePath, long fileSize) {
        this(filePath, 0L, fileSize, 0L);
    }

    @Override
    public String splitId() {
        return filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public long getEndOffset() {
        return endOffset;
    }

    public long getCurrentOffset() {
        return currentOffset;
    }

    /** Returns a copy of this split with an updated read cursor (after partial progress). */
    public FileSplit withCurrentOffset(long newCurrentOffset) {
        return new FileSplit(filePath, startOffset, endOffset, newCurrentOffset);
    }

    /** True when the cursor has reached the end of the split range. */
    public boolean isFinished() {
        return currentOffset >= endOffset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileSplit)) return false;
        FileSplit that = (FileSplit) o;
        return Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    @Override
    public String toString() {
        return "FileSplit{" + filePath + " [" + startOffset + ".." + endOffset
                + "], cursor=" + currentOffset + '}';
    }

    /** Cursor type used by the reader to checkpoint/restore progress within a file. */
    public static final class Cursor implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String filePath;
        public final long offset;

        public Cursor(String filePath, long offset) {
            this.filePath = filePath;
            this.offset = offset;
        }
    }
}
