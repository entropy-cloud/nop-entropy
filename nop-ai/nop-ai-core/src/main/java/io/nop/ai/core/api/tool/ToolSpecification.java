package io.nop.ai.core.api.tool;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.json.JsonSchema;

@DataBean
public class ToolSpecification {
    private String name;
    private String description;
    private Boolean returnDirect;
    private JsonSchema inputSchema;
    private JsonSchema outputSchema;

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

    public JsonSchema getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonSchema inputSchema) {
        this.inputSchema = inputSchema;
    }

    public JsonSchema getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(JsonSchema outputSchema) {
        this.outputSchema = outputSchema;
    }
}
