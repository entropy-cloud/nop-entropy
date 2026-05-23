/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
}
