/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.io.Serializable;
import java.util.Objects;

public class StatePath implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String path;

    private StatePath(String path) {
        this.path = Objects.requireNonNull(path, "path must not be null");
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatePath that = (StatePath) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    public static StatePath forKeyedState(String checkpointNamespace, long epochId,
                                          String operatorId, int subtaskIndex,
                                          int stateShardId, String stateName) {
        return new StatePath(buildPath(checkpointNamespace, epochId, operatorId, subtaskIndex,
                String.valueOf(stateShardId), stateName));
    }

    public static StatePath forOperatorState(String checkpointNamespace, long epochId,
                                             String operatorId, int subtaskIndex,
                                             String stateName) {
        return new StatePath(buildPath(checkpointNamespace, epochId, operatorId, subtaskIndex,
                "operator", stateName));
    }

    public static StatePath forSourceState(String checkpointNamespace, long epochId,
                                           String operatorId, int subtaskIndex,
                                           String splitId) {
        return new StatePath(buildPath(checkpointNamespace, epochId, operatorId, subtaskIndex,
                "source", splitId));
    }

    public static StatePath forSinkState(String checkpointNamespace, long epochId,
                                         String operatorId, int subtaskIndex,
                                         String transactionId) {
        return new StatePath(buildPath(checkpointNamespace, epochId, operatorId, subtaskIndex,
                "sink", transactionId));
    }

    static String buildPath(String checkpointNamespace, long epochId,
                            String operatorId, int subtaskIndex,
                            String category, String stateName) {
        StringBuilder sb = new StringBuilder();
        sb.append("checkpoint").append('/');
        if (checkpointNamespace != null && !checkpointNamespace.isEmpty()) {
            sb.append(checkpointNamespace).append('/');
        }
        sb.append(epochId).append('/');
        sb.append(operatorId).append('/');
        sb.append(subtaskIndex).append('/');
        sb.append(category).append('/');
        sb.append(stateName);
        return sb.toString();
    }
}
