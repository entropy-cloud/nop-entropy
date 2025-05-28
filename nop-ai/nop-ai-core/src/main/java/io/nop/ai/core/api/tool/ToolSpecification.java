package io.nop.ai.core.api.tool;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class ToolSpecification {
    private String name;
    private String description;
    private Boolean returnDirect;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getReturnDirect() {
        return returnDirect;
    }

    public void setReturnDirect(Boolean returnDirect) {
        this.returnDirect = returnDirect;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public Map<String, Object> getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema;
    }
}
