/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import java.io.Serializable;
import java.util.*;

import io.nop.api.core.annotations.data.DataBean;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;

@DataBean
public class StreamModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private final StreamComponents components;
    private final Map<String, Transformation<?>> transformations;

    public StreamModel(StreamComponents components, Map<String, Transformation<?>> transformations) {
        this.components = components != null ? components : new StreamComponents();
        this.transformations = transformations != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(transformations))
                : Collections.emptyMap();
    }

    public StreamModel() {
        this(new StreamComponents(), new LinkedHashMap<>());
    }

    public StreamComponents getComponents() {
        return components;
    }

    public Map<String, Transformation<?>> getTransformations() {
        return transformations;
    }

    public Set<StreamRequirement> getRequirements() {
        return new LinkedHashSet<>(components.getRequirements());
    }

    public StreamModelFingerprint computeFingerprint() {
        StreamModelFingerprint.Builder builder = StreamModelFingerprint.builder();

        for (Map.Entry<String, Transformation<?>> entry : transformations.entrySet()) {
            String valueStr = entry.getValue() != null ? entry.getValue().toString() : "null";
            String transformHash = StreamModelFingerprint.computeSHA256(
                    entry.getKey() + ":" + valueStr);
            builder.addComponentHash(entry.getKey(), transformHash);
        }

        List<String> sortedKeys = new ArrayList<>(transformations.keySet());
        Collections.sort(sortedKeys);
        builder.dagTopologyHash(StreamModelFingerprint.computeSHA256(String.join(",", sortedKeys)));

        List<String> reqNames = new ArrayList<>();
        for (StreamRequirement req : components.getRequirements()) {
            reqNames.add(req.name());
        }
        Collections.sort(reqNames);
        builder.requirementsHash(StreamModelFingerprint.computeSHA256(String.join(",", reqNames)));

        List<String> participantIds = new ArrayList<>(components.getCheckpointParticipants());
        Collections.sort(participantIds);
        builder.checkpointParticipantsHash(StreamModelFingerprint.computeSHA256(String.join(",", participantIds)));

        return builder.build();
    }

    /**
     * Collects consistency capabilities from all source connectors in this pipeline.
     *
     * @return list of source consistency capabilities
     */
    public List<SourceConsistencyCapability> getSourceCapabilities() {
        List<SourceConsistencyCapability> caps = new ArrayList<>();
        for (Transformation<?> t : transformations.values()) {
            if (t instanceof SourceTransformation) {
                SourceFunction<?> fn = ((SourceTransformation<?>) t).getSourceFunction();
                if (fn != null) {
                    caps.add(fn.getSourceConsistency());
                }
            }
        }
        return caps;
    }

    /**
     * Collects consistency capabilities from all sink connectors in this pipeline.
     *
     * @return list of sink consistency capabilities
     */
    public List<SinkConsistencyCapability> getSinkCapabilities() {
        List<SinkConsistencyCapability> caps = new ArrayList<>();
        for (Transformation<?> t : transformations.values()) {
            if (t instanceof SinkTransformation) {
                SinkFunction<?> fn = ((SinkTransformation<?>) t).getSinkFunction();
                if (fn != null) {
                    caps.add(fn.getSinkConsistency());
                }
            }
        }
        return caps;
    }

    @Override
    public String toString() {
        return "StreamModel{" +
                "transformations=" + transformations.size() +
                ", components=" + components +
                '}';
    }
}
