package io.nop.ai.code_analyzer.code;

import io.nop.api.core.annotations.data.DataBean;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@DataBean
public class CodeSymbol {
    private String name;
    private int line;
    private Map<String, String> metadata;

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void intern() {
        name = CodeSymbolInterning.internString(name);
        if (metadata != null) {
            metadata = CodeSymbolInterning.internStringMap(metadata);
        }
    }
}
