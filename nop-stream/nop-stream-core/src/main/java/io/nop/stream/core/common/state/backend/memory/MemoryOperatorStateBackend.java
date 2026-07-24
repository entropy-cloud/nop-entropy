/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.common.state.backend.IOperatorStateBackend;
import io.nop.stream.core.common.state.backend.RedistributionMode;
import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;

public class MemoryOperatorStateBackend implements IOperatorStateBackend {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> operatorStates = new HashMap<>();

    public MemoryOperatorStateBackend() {
    }

    @Override
    public OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
        return new OperatorSnapshotResult(
                new HashMap<>(operatorStates),
                Collections.emptyMap(),
                Collections.emptyMap());
    }

    @Override
    public void restoreState(OperatorSnapshotResult snapshot) throws Exception {
        if (snapshot == null)
            return;
        operatorStates.clear();
        operatorStates.putAll(snapshot.getOperatorStates());
    }

    @Override
    public void restoreState(List<OperatorSnapshotResult> oldSnapshots, int oldParallelism,
                             RedistributionMode mode, int taskIndex, int newParallelism) throws Exception {
        if (mode == null)
            mode = RedistributionMode.NONE;

        switch (mode) {
            case NONE:
                if (oldSnapshots != null && !oldSnapshots.isEmpty()) {
                    restoreState(oldSnapshots.get(0));
                }
                break;
            case UNION:
                restoreUnion(oldSnapshots);
                break;
            case BROADCAST:
                restoreBroadcast(oldSnapshots);
                break;
            case SPLIT_DISTRIBUTE:
                restoreSplitDistribute(oldSnapshots, taskIndex, newParallelism);
                break;
            default:
                throw new StreamException(ERR_STREAM_UNSUPPORTED)
                        .param(ARG_OPERATION, "restoreState with mode=" + mode);
        }
    }

    private void restoreUnion(List<OperatorSnapshotResult> oldSnapshots) {
        Map<String, Object> merged = new HashMap<>();
        if (oldSnapshots != null) {
            for (OperatorSnapshotResult snap : oldSnapshots) {
                if (snap != null) {
                    for (Map.Entry<String, Object> entry : snap.getOperatorStates().entrySet()) {
                        merged.merge(entry.getKey(), entry.getValue(), (oldVal, newVal) -> {
                            if (oldVal instanceof List && newVal instanceof List) {
                                List<Object> combined = new ArrayList<>((List<?>) oldVal);
                                combined.addAll((List<?>) newVal);
                                return combined;
                            }
                            return newVal;
                        });
                    }
                }
            }
        }
        operatorStates.clear();
        operatorStates.putAll(merged);
    }

    private void restoreBroadcast(List<OperatorSnapshotResult> oldSnapshots) {
        operatorStates.clear();
        if (oldSnapshots != null && !oldSnapshots.isEmpty()) {
            OperatorSnapshotResult first = oldSnapshots.stream()
                    .filter(s -> s != null && !s.getOperatorStates().isEmpty())
                    .findFirst().orElse(null);
            if (first != null) {
                operatorStates.putAll(first.getOperatorStates());
            }
        }
    }

    private void restoreSplitDistribute(List<OperatorSnapshotResult> oldSnapshots,
                                        int taskIndex, int newParallelism) {
        Map<String, List<Object>> allNamedEntries = new HashMap<>();
        if (oldSnapshots != null) {
            for (OperatorSnapshotResult snap : oldSnapshots) {
                if (snap != null) {
                    for (Map.Entry<String, Object> entry : snap.getOperatorStates().entrySet()) {
                        String name = entry.getKey();
                        Object val = entry.getValue();
                        List<Object> entries = new ArrayList<>();
                        if (val instanceof List) {
                            entries.addAll((List<?>) val);
                        } else {
                            entries.add(val);
                        }
                        allNamedEntries.computeIfAbsent(name, k -> new ArrayList<>()).addAll(entries);
                    }
                }
            }
        }

        if (newParallelism <= 0)
            newParallelism = 1;
        if (taskIndex < 0)
            taskIndex = 0;

        Map<String, Object> redistributed = new HashMap<>();
        for (Map.Entry<String, List<Object>> namedEntry : allNamedEntries.entrySet()) {
            List<Object> pool = namedEntry.getValue();
            List<Object> myPortion = new ArrayList<>();
            for (int i = taskIndex; i < pool.size(); i += newParallelism) {
                myPortion.add(pool.get(i));
            }
            redistributed.put(namedEntry.getKey(), myPortion);
        }

        operatorStates.clear();
        operatorStates.putAll(redistributed);
    }

    public Map<String, Object> getOperatorStates() {
        return operatorStates;
    }
}
