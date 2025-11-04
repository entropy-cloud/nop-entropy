/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.lattice;

import java.util.Map;

public class VectorClock extends MapLattice<String, Long> {
    public Long getTimestamp(String replicaId) {
        return get(replicaId);
    }

    public void setTimestamp(String replicaId, long timestamp) {
        put(replicaId, new MaxLattice<>(timestamp));
    }

    public int hashCode() {
        return value().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof VectorClock))
            return false;

        VectorClock other = (VectorClock) o;
        return value().equals(other.value());
    }

    public boolean isSame(VectorClock other) {
        if (this == other)
            return true;
        return value().equals(other.value());
    }

    // copy from Hazelcast VectorClock impl
    public boolean isAfter(VectorClock other) {
        boolean anyTimestampGreater = false;
        for (Map.Entry<String, ILattice<Long>> otherEntry : other.value().entrySet()) {
            final String replicaId = otherEntry.getKey();
            final Long otherReplicaTimestamp = otherEntry.getValue().value();
            final Long localReplicaTimestamp = this.getTimestamp(replicaId);

            if (localReplicaTimestamp == null || localReplicaTimestamp < otherReplicaTimestamp) {
                return false;
            } else if (localReplicaTimestamp > otherReplicaTimestamp) {
                anyTimestampGreater = true;
            }
        }
        // there is at least one local timestamp greater or local vector clock has additional timestamps
        return anyTimestampGreater || other.size() < size();
    }
}