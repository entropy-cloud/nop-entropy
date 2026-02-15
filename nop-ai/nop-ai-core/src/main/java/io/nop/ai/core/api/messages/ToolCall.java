package io.nop.ai.core.api.messages;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

/**
 * @deprecated This internal AI core class is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@DataBean
@Deprecated
public class ToolCall {
    private int index;
    private String id;
    private String name;

    private Map<String, Object> arguments;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
