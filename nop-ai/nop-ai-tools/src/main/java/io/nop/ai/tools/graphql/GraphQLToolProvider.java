package io.nop.ai.tools.graphql;

import io.nop.ai.core.api.tool.DefaultAiChatFunctionTool;
import io.nop.ai.core.api.tool.DefaultAiChatToolSet;
import io.nop.ai.core.api.tool.IAiChatFunctionTool;
import io.nop.ai.core.api.tool.IAiChatToolSet;
import io.nop.ai.core.api.tool.ToolSpecification;
import io.nop.ai.core.api.tool.ToolSpecificationLoader;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.schema.meta.GraphQLToJsonSchema;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GraphQLToolProvider {
    private final Map<String, IAiChatFunctionTool> cache = new ConcurrentHashMap<>();

    private final IGraphQLEngine graphqlEngine;

    public GraphQLToolProvider(IGraphQLEngine graphqlEngine) {
        this.graphqlEngine = graphqlEngine;
    }

    public IAiChatFunctionTool getTool(String toolName) {
        return cache.computeIfAbsent(toolName, this::buildTool);
    }

    public IAiChatToolSet getToolSet(Set<String> toolNames) {
        DefaultAiChatToolSet toolSet = new DefaultAiChatToolSet();
        if (toolNames != null && !toolNames.isEmpty()) {
            toolNames.forEach(opName -> toolSet.addFunction(getTool(opName)));
        }
        return toolSet;
    }

    public IAiChatToolSet getToolsByPrefix(String prefix) {
        List<GraphQLFieldDefinition> opList = graphqlEngine.getSchemaLoader().getOperationDefinitions(null);
        Set<String> toolNames = new LinkedHashSet<>();
        List<String> prefixList = ConvertHelper.toCsvList(prefix, NopException::new);

        opList.forEach(op -> {
            for (String prefixItem : prefixList) {
                if (op.getName().startsWith(prefixItem))
                    toolNames.add(op.getName());
            }
        });
        return getToolSet(toolNames);
    }

    protected IAiChatFunctionTool buildTool(String toolName) {
        DefaultAiChatFunctionTool tool = new DefaultAiChatFunctionTool();
        ToolSpecification spec = loadToolSpec(toolName);
        tool.setName(toolName);
        tool.setDescription(spec.getDescription());
        tool.setInputSchema(spec.getInputSchema());
        tool.setOutputSchema(spec.getOutputSchema());
        String operationName = spec.getName() == null ? toolName : spec.getName();
        tool.setInvoker(args -> callTool(operationName, args));
        return tool;
    }

    protected ToolSpecification loadToolSpec(String operationName) {
        ToolSpecification spec = ToolSpecificationLoader.loadSpecification(operationName);
        if (spec != null) {
            return spec;
        }

        spec = new ToolSpecification();
        spec.setName(operationName);
        GraphQLFieldDefinition op = graphqlEngine.getOperationDefinition(null, operationName);
        spec.setDescription(op.getDescription());
        spec.setInputSchema(GraphQLToJsonSchema.INSTANCE.argsToJsonSchema(op.getArguments()));
        return spec;
    }

    protected Object callTool(String operationName, Map<String, Object> args) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(args);
        IGraphQLExecutionContext ctx = graphqlEngine.newRpcContext(null, operationName, request);
        return graphqlEngine.executeRpcAsync(ctx).thenApply(response -> {
            if (response.isOk()) {
                if (response.getData() instanceof String)
                    return response.getData();
                return JsonTool.stringify(response.getData());
            } else {
                return JsonTool.stringify(response);
            }
        });
    }
}