/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Stage 49 Phase 3: serializable snapshot of {@link FileSplitEnumerator} bookkeeping.
 * Round-trips through {@code EpochManifest.sourceEnumeratorSnapshots} via the
 * {@link FileSource}'s {@link io.nop.stream.core.source.SimpleVersionedSerializer}.
 */
@DataBean
public final class FileSplitEnumeratorState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String directoryPath;
    private final Set<String> discoveredFiles;
    private final Set<String> assignedFiles;
    private final Set<String> finishedFiles;
    private final Map<String, FileSplit> splitById;
    private final int nextSubtaskIndex;

    public FileSplitEnumeratorState(String directoryPath,
                                    Set<String> discoveredFiles,
                                    Set<String> assignedFiles,
                                    Set<String> finishedFiles,
                                    Map<String, FileSplit> splitById,
                                    int nextSubtaskIndex) {
        this.directoryPath = directoryPath;
        this.discoveredFiles = discoveredFiles != null ? new LinkedHashSet<>(discoveredFiles) : new LinkedHashSet<>();
        this.assignedFiles = assignedFiles != null ? new LinkedHashSet<>(assignedFiles) : new LinkedHashSet<>();
        this.finishedFiles = finishedFiles != null ? new LinkedHashSet<>(finishedFiles) : new LinkedHashSet<>();
        this.splitById = splitById != null ? new LinkedHashMap<>(splitById) : new LinkedHashMap<>();
        this.nextSubtaskIndex = nextSubtaskIndex;
    }

    public FileSplitEnumeratorState() {
        this(null, null, null, null, null, 0);
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    public Set<String> getDiscoveredFiles() {
        return discoveredFiles;
    }

    public Set<String> getAssignedFiles() {
        return assignedFiles;
    }

    public Set<String> getFinishedFiles() {
        return finishedFiles;
    }

    public Map<String, FileSplit> getSplitById() {
        return splitById;
    }

    public int getNextSubtaskIndex() {
        return nextSubtaskIndex;
    }
}
