package io.nop.ai.core.api.tool;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.json.JsonSchema;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IFunctionModel;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.nop.core.type.utils.GenericTypeToJsonSchema.buildInputSchema;
import static io.nop.core.type.utils.GenericTypeToJsonSchema.buildOutputSchema;

/**
 * @deprecated This internal AI core class is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@Deprecated
public class DefaultAiChatFunctionTool implements IAiChatFunctionTool {
    private String name;
    private String description;
    private JsonSchema inputSchema;
    private JsonSchema outputSchema;
    private Boolean returnDirect;

    private ToolSpecification spec;

    private Function<Map<String, Object>, Object> invoker;

    public static DefaultAiChatFunctionTool fromMethod(IFunctionModel func) {
        DefaultAiChatFunctionTool ret = new DefaultAiChatFunctionTool();
        String toolName = getServiceName(func) + "__" + func.getName();
        ret.setName(toolName);
        ret.setDescription(func.getDescription());
        ret.setInputSchema(buildInputSchema(func));
        ret.setOutputSchema(buildOutputSchema(func));
        ret.setInvoker(args -> func.invokeWithNamedArgs(DisabledEvalScope.INSTANCE, args));

        String serviceName = getServiceName(func);
        ToolSpecification spec = ToolSpecificationLoader.loadSpecification(toolName);
        if (spec != null) {
            ret.setName(spec.getName());
            ret.setDescription(spec.getDescription());
            ret.setInputSchema(spec.getInputSchema());
            ret.setOutputSchema(spec.getOutputSchema());
        }
        return ret;
    }

    static String getServiceName(IFunctionModel fn) {
        Class<?> clazz = fn.getDeclaringClass();
        BizModel bizModel = clazz.getAnnotation(BizModel.class);
        if (bizModel != null)
            return bizModel.value();
        return clazz.getSimpleName();
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public JsonSchema getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonSchema inputSchema) {
        this.inputSchema = inputSchema;
    }

    @Override
    public JsonSchema getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(JsonSchema outputSchema) {
        this.outputSchema = outputSchema;
    }

    public Function<Map<String, Object>, Object> getInvoker() {
        return invoker;
    }

    public void setInvoker(Function<Map<String, Object>, Object> invoker) {
        this.invoker = invoker;
    }

    @Override
    public Boolean getReturnDirect() {
        return returnDirect;
    }

    public void setReturnDirect(Boolean returnDirect) {
        this.returnDirect = returnDirect;
    }

    @Override
    public Object callTool(Map<String, Object> args) {
        return FutureHelper.getResult(invoker.apply(args));
    }

    @Override
    public CompletionStage<Object> callToolAsync(Map<String, Object> args) {
        return FutureHelper.futureCall(() -> invoker.apply(args));
    }

    @Override
    public ToolSpecification toSpec() {
        ToolSpecification spec = this.spec;
        if (spec == null) {
            spec = new ToolSpecification();
            spec.setName(name);
            spec.setDescription(description);
            spec.setInputSchema(inputSchema);
            spec.setOutputSchema(outputSchema);
            this.spec = spec;
        }
        return spec;
    }
}
