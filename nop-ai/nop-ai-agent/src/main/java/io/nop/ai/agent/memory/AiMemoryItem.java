package io.nop.ai.agent.memory;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AiMemoryItem {
    private String key;
    private Object value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
