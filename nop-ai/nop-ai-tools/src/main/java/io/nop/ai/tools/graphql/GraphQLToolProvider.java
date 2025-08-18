package io.nop.ai.tools.graphql;

import io.nop.ai.core.api.tool.DefaultAiChatFunctionTool;
import io.nop.ai.core.api.tool.DefaultAiChatToolSet;
import io.nop.ai.core.api.tool.IAiChatFunctionTool;
import io.nop.ai.core.api.tool.IAiChatToolSet;
import io.nop.ai.core.api.tool.ToolSpecification;
import io.nop.ai.core.api.tool.ToolSpecificationLoader;
import io.nop.api.core.beans.ApiRequest;
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

    private IGraphQLEngine graphqlEngine;

    public IAiChatFunctionTool getTool(String operationName) {
        return cache.computeIfAbsent(operationName, this::buildTool);
    }

    public IAiChatToolSet getToolSet(Set<String> operationNames) {
        DefaultAiChatToolSet toolSet = new DefaultAiChatToolSet();
        if (operationNames != null && !operationNames.isEmpty()) {
            operationNames.forEach(opName -> toolSet.addFunction(getTool(opName)));
        }
        return toolSet;
    }

    public IAiChatToolSet getToolsByPrefix(String prefix) {
        List<GraphQLFieldDefinition> opList = graphqlEngine.getSchemaLoader().getOperationDefinitions(null);
        Set<String> opNames = new LinkedHashSet<>();
        opList.forEach(op -> opNames.add(op.getName()));
        return getToolSet(opNames);
    }

    protected IAiChatFunctionTool buildTool(String operationName) {
        DefaultAiChatFunctionTool tool = new DefaultAiChatFunctionTool();
        ToolSpecification spec = loadToolSpec(operationName);
        tool.setName(operationName);
        tool.setDescription(spec.getDescription());
        tool.setInputSchema(spec.getInputSchema());
        tool.setOutputSchema(spec.getOutputSchema());
        tool.setInvoker(args -> callTool(operationName, args));
        return tool;
    }

    protected ToolSpecification loadToolSpec(String operationName) {
        ToolSpecification spec = ToolSpecificationLoader.loadSpecification(operationName);
        if (spec != null)
            return spec;

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
        return graphqlEngine.executeRpc(ctx).get();
    }
}
