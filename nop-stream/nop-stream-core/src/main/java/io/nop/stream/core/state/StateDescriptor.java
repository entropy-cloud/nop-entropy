package io.nop.stream.core.state;

import java.io.Serializable;

public class StateDescriptor<T> implements Serializable {
    private final String name;
    private final Class<T> valueType;

    public StateDescriptor(String name, Class<T> valueType) {
        this.name = name;
        this.valueType = valueType;
    }

    public String getName() {
        return name;
    }

    public Class<T> getValueType() {
        return valueType;
    }
}
