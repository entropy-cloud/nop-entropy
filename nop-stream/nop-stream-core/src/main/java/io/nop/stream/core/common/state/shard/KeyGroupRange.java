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

import io.nop.api.core.annotations.core.Internal;

/**
 * Half-open contiguous range of key groups {@code [startKeyGroup, endKeyGroup)}.
 *
 * <p>Stage 35 will consume these ranges to perform range-intersection partial
 * restore (each subtask owns a contiguous {@code KeyGroupRange} and only reads
 * the SST/segment bytes whose group prefix falls inside its range). This class
 * delivers the set operations that range-restore needs (intersection,
 * containment, split), backed by focused tests; the production restore wiring
 * itself is Stage 35.
 */
@Internal
public final class KeyGroupRange implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final KeyGroupRange EMPTY = new KeyGroupRange(0, 0);

    private final int startKeyGroup;
    private final int endKeyGroup;

    /**
     * @param startKeyGroup inclusive lower bound (&ge; 0)
     * @param endKeyGroup   exclusive upper bound (&ge; {@code startKeyGroup})
     */
    public KeyGroupRange(int startKeyGroup, int endKeyGroup) {
        if (startKeyGroup < 0) {
            throw new IllegalArgumentException("startKeyGroup must be non-negative: " + startKeyGroup);
        }
        if (endKeyGroup < startKeyGroup) {
            throw new IllegalArgumentException(
                    "endKeyGroup (" + endKeyGroup + ") must be >= startKeyGroup (" + startKeyGroup + ")");
        }
        this.startKeyGroup = startKeyGroup;
        this.endKeyGroup = endKeyGroup;
    }

    public int getStartKeyGroup() {
        return startKeyGroup;
    }

    public int getEndKeyGroup() {
        return endKeyGroup;
    }

    /**
     * Number of key groups in this range. {@code 0} for an empty range,
     * {@code 1} for a point range.
     */
    public int getNumberOfKeyGroups() {
        return endKeyGroup - startKeyGroup;
    }

    public boolean isEmpty() {
        return startKeyGroup == endKeyGroup;
    }

    /**
     * @return {@code true} if the range contains exactly one key group.
     */
    public boolean isPointRange() {
        return getNumberOfKeyGroups() == 1;
    }

    public boolean contains(int keyGroup) {
        return keyGroup >= startKeyGroup && keyGroup < endKeyGroup;
    }

    public boolean contains(KeyGroupRange other) {
        if (other == null) {
            return false;
        }
        return other.startKeyGroup >= this.startKeyGroup && other.endKeyGroup <= this.endKeyGroup;
    }

    /**
     * Return the intersection of this range with {@code other}. Returns an
     * {@link #isEmpty empty} range when the two ranges are disjoint.
     */
    public KeyGroupRange intersect(KeyGroupRange other) {
        if (other == null) {
            return EMPTY;
        }
        int s = Math.max(this.startKeyGroup, other.startKeyGroup);
        int e = Math.min(this.endKeyGroup, other.endKeyGroup);
        if (e <= s) {
            // Disjoint or merely adjacent ranges share no key group; return the
            // canonical empty range so all empty intersections compare equal.
            return EMPTY;
        }
        return new KeyGroupRange(s, e);
    }

    /**
     * @return {@code true} iff this range and {@code other} share at least one
     * key group (i.e. their intersection is non-empty).
     */
    public boolean overlaps(KeyGroupRange other) {
        return other != null && !intersect(other).isEmpty();
    }

    /**
     * @return {@code true} iff this range is immediately adjacent to (but not
     * overlapping) {@code other}, so the two could be merged into one
     * contiguous range.
     */
    public boolean isAdjacent(KeyGroupRange other) {
        if (other == null) {
            return false;
        }
        return this.endKeyGroup == other.startKeyGroup || other.endKeyGroup == this.startKeyGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyGroupRange that = (KeyGroupRange) o;
        return startKeyGroup == that.startKeyGroup && endKeyGroup == that.endKeyGroup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startKeyGroup, endKeyGroup);
    }

    @Override
    public String toString() {
        return "KeyGroupRange[" + startKeyGroup + "," + endKeyGroup + ")";
    }
}
