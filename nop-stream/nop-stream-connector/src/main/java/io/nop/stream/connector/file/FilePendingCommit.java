/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.Serializable;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Serializable descriptor of a pending file-sink commit for one checkpoint epoch.
 *
 * <p>Stores the temp-file path (as a {@code String}, because {@code java.nio.file.Path} is not
 * {@code Serializable}), the number of records buffered in that epoch, and the owning
 * subtask index. {@code commit(epochId)} consumes this descriptor to perform the atomic
 * rename; the owner index keeps the final path / manifest key stable even when a
 * different subtask copy re-commits the entry after recovery.
 */
public class FilePendingCommit implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String tempPath;
    private final int recordCount;
    /**
     * Subtask index of the sink copy that created this entry (suffixes output paths).
     * Defaults to 0 for entries created by a non-parallel sink (backward compatible).
     */
    private final int subtaskIndex;

    public FilePendingCommit(String tempPath, int recordCount) {
        this(tempPath, recordCount, 0);
    }

    public FilePendingCommit(String tempPath, int recordCount, int subtaskIndex) {
        if (tempPath == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "tempPath");
        }
        this.tempPath = tempPath;
        this.recordCount = recordCount;
        this.subtaskIndex = subtaskIndex;
    }

    public String getTempPath() {
        return tempPath;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    @Override
    public String toString() {
        return "FilePendingCommit{tempPath='" + tempPath + "', recordCount=" + recordCount
                + ", subtaskIndex=" + subtaskIndex + "}";
    }
}
