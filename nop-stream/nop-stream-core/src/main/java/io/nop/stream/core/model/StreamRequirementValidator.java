/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.exceptions.StreamException;

public class StreamRequirementValidator {

    public static void validate(StreamModel model, StreamBackendCapability capability) {
        if (model == null) {
            throw new IllegalArgumentException("StreamModel must not be null");
        }
        if (capability == null) {
            throw new IllegalArgumentException("StreamBackendCapability must not be null");
        }

        List<String> errors = new ArrayList<>();

        for (StreamRequirement requirement : model.getRequirements()) {
            if (!capability.supports(requirement)) {
                errors.add("Pipeline requires " + requirement.name()
                        + " but backend does not support it");
            }
        }

        if (!errors.isEmpty()) {
            throw new StreamException("Stream requirement validation failed: " + String.join("; ", errors));
        }
    }

    public static void validateStrictExactlyOnce(Set<StreamRequirement> requirements,
                                                  boolean sourceReplayable,
                                                  boolean sinkTwoPhaseCommit) {
        if (!requirements.contains(StreamRequirement.STRICT_EXACTLY_ONCE)) {
            return;
        }

        List<String> errors = new ArrayList<>();
        if (!sourceReplayable) {
            errors.add("STRICT_EXACTLY_ONCE requires source to be REPLAYABLE");
        }
        if (!sinkTwoPhaseCommit) {
            errors.add("STRICT_EXACTLY_ONCE requires sink to support TWO_PHASE_COMMIT");
        }

        if (!errors.isEmpty()) {
            throw new StreamException("STRICT_EXACTLY_ONCE validation failed: " + String.join("; ", errors));
        }
    }

    /**
     * Validates that connector consistency capabilities meet the pipeline's processing guarantee requirements.
     * <p>
     * When the pipeline declares {@link io.nop.stream.core.checkpoint.ProcessingGuarantee#STRICT_EXACTLY_ONCE}:
     * <ul>
     *   <li>All sources must have at least {@link SourceConsistencyCapability#REPLAYABLE} capability
     *       (REPLAYABLE or TRANSACTIONAL_READ are acceptable)</li>
     *   <li>All sinks must have at least {@link SinkConsistencyCapability#TWO_PHASE_COMMIT} capability
     *       (TWO_PHASE_COMMIT or STAGED_ATOMIC_COMMIT are acceptable)</li>
     * </ul>
     *
     * @param guarantee          the pipeline's processing guarantee requirement
     * @param sourceCapabilities consistency capabilities of all source connectors
     * @param sinkCapabilities   consistency capabilities of all sink connectors
     * @throws StreamException if any connector does not meet the required capability level
     */
    public static void validateConnectorConsistency(
            io.nop.stream.core.checkpoint.ProcessingGuarantee guarantee,
            List<SourceConsistencyCapability> sourceCapabilities,
            List<SinkConsistencyCapability> sinkCapabilities) {
        if (guarantee != io.nop.stream.core.checkpoint.ProcessingGuarantee.STRICT_EXACTLY_ONCE) {
            return;
        }

        List<String> errors = new ArrayList<>();

        for (int i = 0; i < sourceCapabilities.size(); i++) {
            SourceConsistencyCapability cap = sourceCapabilities.get(i);
            if (!isSourceReplayableOrStronger(cap)) {
                errors.add("source[" + i + "] has capability " + cap.name()
                        + " but STRICT_EXACTLY_ONCE requires at least REPLAYABLE");
            }
        }

        for (int i = 0; i < sinkCapabilities.size(); i++) {
            SinkConsistencyCapability cap = sinkCapabilities.get(i);
            if (!isSinkTwoPhaseCommitOrStronger(cap)) {
                errors.add("sink[" + i + "] has capability " + cap.name()
                        + " but STRICT_EXACTLY_ONCE requires at least TWO_PHASE_COMMIT");
            }
        }

        if (!errors.isEmpty()) {
            throw new StreamException("Connector consistency validation failed for STRICT_EXACTLY_ONCE: "
                    + String.join("; ", errors));
        }
    }

    private static boolean isSourceReplayableOrStronger(SourceConsistencyCapability cap) {
        return cap == SourceConsistencyCapability.REPLAYABLE
                || cap == SourceConsistencyCapability.TRANSACTIONAL_READ;
    }

    private static boolean isSinkTwoPhaseCommitOrStronger(SinkConsistencyCapability cap) {
        return cap == SinkConsistencyCapability.TWO_PHASE_COMMIT
                || cap == SinkConsistencyCapability.STAGED_ATOMIC_COMMIT;
    }
}
