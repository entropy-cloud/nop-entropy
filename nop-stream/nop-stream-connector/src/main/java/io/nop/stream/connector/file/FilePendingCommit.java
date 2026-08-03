/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.Serializable;

/**
 * Serializable descriptor of a pending file-sink commit for one checkpoint epoch.
 *
 * <p>Stores the temp-file path (as a {@code String}, because {@code java.nio.file.Path} is not
 * {@code Serializable}) and the number of records buffered in that epoch. {@code commit(epochId)}
 * consumes this descriptor to perform the atomic rename.
 */
public class FilePendingCommit implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String tempPath;
    private final int recordCount;

    public FilePendingCommit(String tempPath, int recordCount) {
        if (tempPath == null) {
            throw new IllegalArgumentException("tempPath must not be null");
        }
        this.tempPath = tempPath;
        this.recordCount = recordCount;
    }

    public String getTempPath() {
        return tempPath;
    }

    public int getRecordCount() {
        return recordCount;
    }

    @Override
    public String toString() {
        return "FilePendingCommit{tempPath='" + tempPath + "', recordCount=" + recordCount + "}";
    }
}
