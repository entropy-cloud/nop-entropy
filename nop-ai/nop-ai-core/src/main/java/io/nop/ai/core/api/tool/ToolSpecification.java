package io.nop.ai.core.api.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.json.JsonSchema;

/**
 * @deprecated This internal AI core class is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@DataBean
@Deprecated
public class ToolSpecification {
    private String name;
    private String description;
    private JsonSchema inputSchema;
    private JsonSchema outputSchema;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public JsonSchema getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonSchema inputSchema) {
        this.inputSchema = inputSchema;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public JsonSchema getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(JsonSchema outputSchema) {
        this.outputSchema = outputSchema;
    }
}
