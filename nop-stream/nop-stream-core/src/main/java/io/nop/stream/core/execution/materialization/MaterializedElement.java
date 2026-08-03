/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.materialization;

import java.io.Serializable;

import io.nop.stream.core.streamrecord.StreamElement;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * A {@link StreamElement} paired with the producer epoch at the time it was
 * materialized. This is the unit returned by {@link IMaterializationPoint#replay(long)}
 * and {@link IMaterializationPoint#replayAll()}.
 *
 * <p>The epoch tag is the data-plane foundation for successor plan 4's
 * consistent-cut alignment protocol: replay-start selection reads the epoch of
 * each materialized element to choose a safe replay point.
 *
 * @see IMaterializationPoint
 */
public final class MaterializedElement implements Serializable {

    private static final long serialVersionUID = 1L;

    private final StreamElement element;
    private final long epoch;

    /**
     * @param element the materialized stream element (must not be null)
     * @param epoch   the producer epoch at write time
     */
    public MaterializedElement(StreamElement element, long epoch) {
        if (element == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "element");
        }
        this.element = element;
        this.epoch = epoch;
    }

    /**
     * @return the materialized stream element (never null)
     */
    public StreamElement getElement() {
        return element;
    }

    /**
     * @return the producer epoch at the time this element was materialized
     */
    public long getEpoch() {
        return epoch;
    }

    @Override
    public String toString() {
        return "MaterializedElement{epoch=" + epoch + ", element=" + element + '}';
    }
}
