/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.graph.StreamEdge;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.windowing.WindowingStrategy;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;

@DataBean
public class StreamComponents implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Transformation<?>> transforms;
    private final Map<String, StreamEdge> streams;
    private final Map<String, WindowingStrategy> windowingStrategies;
    private final List<StreamRequirement> requirements;
    private final Set<String> checkpointParticipants;
    private IWindowOperatorFactory windowOperatorFactory;

    public StreamComponents() {
        this.transforms = new LinkedHashMap<>();
        this.streams = new LinkedHashMap<>();
        this.windowingStrategies = new LinkedHashMap<>();
        this.requirements = new ArrayList<>();
        this.checkpointParticipants = new LinkedHashSet<>();
    }

    public StreamComponents(Map<String, Transformation<?>> transforms,
                            Map<String, StreamEdge> streams,
                            Map<String, WindowingStrategy> windowingStrategies,
                            List<StreamRequirement> requirements,
                            Set<String> checkpointParticipants,
                            IWindowOperatorFactory windowOperatorFactory) {
        this.transforms = transforms != null ? new LinkedHashMap<>(transforms) : new LinkedHashMap<>();
        this.streams = streams != null ? new LinkedHashMap<>(streams) : new LinkedHashMap<>();
        this.windowingStrategies = windowingStrategies != null ? new LinkedHashMap<>(windowingStrategies) : new LinkedHashMap<>();
        this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
        this.checkpointParticipants = checkpointParticipants != null ? new LinkedHashSet<>(checkpointParticipants) : new LinkedHashSet<>();
        this.windowOperatorFactory = windowOperatorFactory;
    }

    public Map<String, Transformation<?>> getTransforms() {
        return Collections.unmodifiableMap(transforms);
    }

    public Map<String, StreamEdge> getStreams() {
        return Collections.unmodifiableMap(streams);
    }

    public Map<String, WindowingStrategy> getWindowingStrategies() {
        return Collections.unmodifiableMap(windowingStrategies);
    }

    public List<StreamRequirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    public Set<String> getCheckpointParticipants() {
        return Collections.unmodifiableSet(checkpointParticipants);
    }

    public void registerTransform(String id, Transformation<?> transform) {
        if (id == null || id.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "id");
        }
        if (transform == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "transform");
        }
        transforms.put(id, transform);
    }

    public Transformation<?> getTransform(String id) {
        return transforms.get(id);
    }

    public void registerStream(String id, StreamEdge stream) {
        if (id == null || id.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "id");
        }
        if (stream == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "stream");
        }
        streams.put(id, stream);
    }

    public StreamEdge getStream(String id) {
        return streams.get(id);
    }

    public void registerWindowingStrategy(String id, WindowingStrategy strategy) {
        if (id == null || id.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "id");
        }
        if (strategy == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "strategy");
        }
        windowingStrategies.put(id, strategy);
    }

    public WindowingStrategy getWindowingStrategy(String id) {
        return windowingStrategies.get(id);
    }

    @Internal
    public <T> T getBean(String id, Class<T> clazz) {
        if (id == null || id.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "id");
        }
        if (clazz == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "clazz");
        }
        Object bean = lookupAcrossRegistries(id);
        if (bean == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Bean not found: " + id);
        }
        if (!clazz.isInstance(bean)) {
            throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                    .param(ARG_EXPECTED_TYPE, clazz.getName())
                    .param(ARG_ACTUAL_TYPE, bean.getClass().getName())
                    .param(ARG_DETAIL, "Bean '" + id + "' is not of expected type");
        }
        return clazz.cast(bean);
    }

    private Object lookupAcrossRegistries(String id) {
        Object bean = transforms.get(id);
        if (bean != null) return bean;
        bean = streams.get(id);
        if (bean != null) return bean;
        return windowingStrategies.get(id);
    }

    public void addRequirement(StreamRequirement requirement) {
        if (requirement == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "requirement");
        }
        if (!requirements.contains(requirement)) {
            requirements.add(requirement);
        }
    }

    public void addCheckpointParticipant(String operatorId) {
        if (operatorId == null || operatorId.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorId");
        }
        checkpointParticipants.add(operatorId);
    }

    public boolean hasCheckpointParticipant(String operatorId) {
        return checkpointParticipants.contains(operatorId);
    }

    public IWindowOperatorFactory getWindowOperatorFactory() {
        return windowOperatorFactory;
    }

    public void setWindowOperatorFactory(IWindowOperatorFactory windowOperatorFactory) {
        this.windowOperatorFactory = windowOperatorFactory;
    }

    @Override
    public String toString() {
        return "StreamComponents{" +
                "transforms=" + transforms.size() +
                ", streams=" + streams.size() +
                ", windowingStrategies=" + windowingStrategies.size() +
                ", requirements=" + requirements.size() +
                ", checkpointParticipants=" + checkpointParticipants.size() +
                '}';
    }
}
