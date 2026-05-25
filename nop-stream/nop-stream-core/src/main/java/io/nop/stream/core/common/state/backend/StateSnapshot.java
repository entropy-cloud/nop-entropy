package io.nop.stream.core.common.state.backend;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class StateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> stateData;

    public StateSnapshot() {
        this.stateData = new LinkedHashMap<>();
    }

    public StateSnapshot(Map<String, Object> stateData) {
        this.stateData = stateData != null ? stateData : new LinkedHashMap<>();
    }

    public Map<String, Object> getStateData() {
        return stateData;
    }

    public boolean isEmpty() {
        return stateData.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getStates() {
        Map<String, Object> states = (Map<String, Object>) stateData.get("states");
        return states != null ? states : Collections.emptyMap();
    }

    public String getKeyType() {
        return (String) stateData.get("keyType");
    }

    @Override
    public String toString() {
        return "StateSnapshot{keyType=" + getKeyType() + ", stateCount=" + getStates().size() + "}";
    }
}
