/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Content-addressed handle to a single RocksDB SST file (or any immutable state
 * file). The {@link #getContentHash()} (SHA-256 of the file content) doubles as
 * the {@code stateObjectId} used by {@link SharedStateRegistry}: two handles
 * with the same content hash refer to the same logical state object and are
 * de-duplicated across checkpoints.
 *
 * <p>This class is immutable and serializable so it can travel through the ACK
 * channel from task to coordinator.
 */
@DataBean
public final class SharedStateHandle implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SHA-256 hex of the file content. Also the registry state-object id. */
    private final String contentHash;
    /** Local file path at snapshot time (may be a temp checkpoint dir). */
    private final String filePath;
    /** File size in bytes. */
    private final long size;

    public SharedStateHandle(String contentHash, String filePath, long size) {
        if (contentHash == null || contentHash.isEmpty()) {
            throw new IllegalArgumentException("contentHash must not be null or empty");
        }
        this.contentHash = contentHash;
        this.filePath = filePath;
        this.size = size;
    }

    /** Content-addressed identity; identical to {@link #getContentHash()}. */
    public String getStateObjectId() {
        return contentHash;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "SharedStateHandle{contentHash=" + contentHash + ", size=" + size + "}";
    }

    @Override
    public int hashCode() {
        return contentHash.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SharedStateHandle)) {
            return false;
        }
        return contentHash.equals(((SharedStateHandle) o).contentHash);
    }
}
