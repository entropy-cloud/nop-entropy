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

@DataBean
public class StreamComponents implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> transforms;
    private final Map<String, Object> streams;
    private final Map<String, Object> windowingStrategies;
    private final Map<String, Object> coders;
    private final Map<String, Object> schemas;
    private final Map<String, Object> environments;
    private final Map<String, Object> sideInputs;
    private final List<StreamRequirement> requirements;
    private final Set<String> checkpointParticipants;

    public StreamComponents() {
        this.transforms = new LinkedHashMap<>();
        this.streams = new LinkedHashMap<>();
        this.windowingStrategies = new LinkedHashMap<>();
        this.coders = new LinkedHashMap<>();
        this.schemas = new LinkedHashMap<>();
        this.environments = new LinkedHashMap<>();
        this.sideInputs = new LinkedHashMap<>();
        this.requirements = new ArrayList<>();
        this.checkpointParticipants = new LinkedHashSet<>();
    }

    public StreamComponents(Map<String, Object> transforms,
                            Map<String, Object> streams,
                            Map<String, Object> windowingStrategies,
                            Map<String, Object> coders,
                            Map<String, Object> schemas,
                            Map<String, Object> environments,
                            Map<String, Object> sideInputs,
                            List<StreamRequirement> requirements,
                            Set<String> checkpointParticipants) {
        this.transforms = transforms != null ? new LinkedHashMap<>(transforms) : new LinkedHashMap<>();
        this.streams = streams != null ? new LinkedHashMap<>(streams) : new LinkedHashMap<>();
        this.windowingStrategies = windowingStrategies != null ? new LinkedHashMap<>(windowingStrategies) : new LinkedHashMap<>();
        this.coders = coders != null ? new LinkedHashMap<>(coders) : new LinkedHashMap<>();
        this.schemas = schemas != null ? new LinkedHashMap<>(schemas) : new LinkedHashMap<>();
        this.environments = environments != null ? new LinkedHashMap<>(environments) : new LinkedHashMap<>();
        this.sideInputs = sideInputs != null ? new LinkedHashMap<>(sideInputs) : new LinkedHashMap<>();
        this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
        this.checkpointParticipants = checkpointParticipants != null ? new LinkedHashSet<>(checkpointParticipants) : new LinkedHashSet<>();
    }

    public Map<String, Object> getTransforms() {
        return Collections.unmodifiableMap(transforms);
    }

    public Map<String, Object> getStreams() {
        return Collections.unmodifiableMap(streams);
    }

    public Map<String, Object> getWindowingStrategies() {
        return Collections.unmodifiableMap(windowingStrategies);
    }

    public Map<String, Object> getCoders() {
        return Collections.unmodifiableMap(coders);
    }

    public Map<String, Object> getSchemas() {
        return Collections.unmodifiableMap(schemas);
    }

    public Map<String, Object> getEnvironments() {
        return Collections.unmodifiableMap(environments);
    }

    public Map<String, Object> getSideInputs() {
        return Collections.unmodifiableMap(sideInputs);
    }

    public List<StreamRequirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    public Set<String> getCheckpointParticipants() {
        return Collections.unmodifiableSet(checkpointParticipants);
    }

    public void registerTransform(String id, Object transform) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Transform ID must not be null or empty");
        }
        transforms.put(id, transform);
    }

    public Object getTransform(String id) {
        return transforms.get(id);
    }

    public void registerStream(String id, Object stream) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Stream ID must not be null or empty");
        }
        streams.put(id, stream);
    }

    public Object getStream(String id) {
        return streams.get(id);
    }

    public void registerWindowingStrategy(String id, Object strategy) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("WindowingStrategy ID must not be null or empty");
        }
        windowingStrategies.put(id, strategy);
    }

    public Object getWindowingStrategy(String id) {
        return windowingStrategies.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(String id, Class<T> clazz) {
        Object bean = windowingStrategies.get(id);
        if (bean == null) {
            throw new IllegalArgumentException("Bean not found: " + id);
        }
        return (T) bean;
    }

    public void addRequirement(StreamRequirement requirement) {
        if (requirement == null) {
            throw new IllegalArgumentException("Requirement must not be null");
        }
        if (!requirements.contains(requirement)) {
            requirements.add(requirement);
        }
    }

    public void addCheckpointParticipant(String operatorId) {
        if (operatorId == null || operatorId.isEmpty()) {
            throw new IllegalArgumentException("Operator ID must not be null or empty");
        }
        checkpointParticipants.add(operatorId);
    }

    public boolean hasCheckpointParticipant(String operatorId) {
        return checkpointParticipants.contains(operatorId);
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
