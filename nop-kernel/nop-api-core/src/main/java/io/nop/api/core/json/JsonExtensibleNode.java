package io.nop.api.core.json;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonExtensibleNode {
    private Map<String, Object> extProperties;

    @JsonAnyGetter
    public Map<String, Object> getExtProperties() {
        return extProperties;
    }

    public void setExtProperties(Map<String, Object> extProperties) {
        this.extProperties = extProperties;
    }

    @JsonAnySetter
    public void setExtProperty(String name, Object value) {
        if (name.indexOf(':') > 0 || name.startsWith("x-")) {
            if (extProperties == null)
                extProperties = new LinkedHashMap<>();
            extProperties.put(name, value);
        } else {
            throw new IllegalArgumentException("invalid property name: " + name);
        }
    }
}
