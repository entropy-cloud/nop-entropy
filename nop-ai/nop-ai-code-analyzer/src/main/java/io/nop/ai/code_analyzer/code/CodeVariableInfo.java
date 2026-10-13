package io.nop.ai.code_analyzer.code;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Objects;

@DataBean
public class CodeVariableInfo {
    private String name;
    private String type;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void intern() {
        name = CodeSymbolInterning.internString(name);
        type = CodeSymbolInterning.internString(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeVariableInfo that = (CodeVariableInfo) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }
}
