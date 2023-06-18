package io.nop.router;

import java.util.List;

public class RouteValue<V> {
    private final List<String> varNames;
    private final V value;

    public RouteValue(List<String> varNames, V value) {
        this.varNames = varNames;
        this.value = value;
    }

    public List<String> getVarNames() {
        return varNames;
    }

    public V getValue() {
        return value;
    }
}
