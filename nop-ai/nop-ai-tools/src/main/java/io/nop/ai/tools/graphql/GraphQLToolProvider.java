package io.nop.ai.tools.graphql;

import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.core.command.IAiToolInvoker;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exposes GraphQL operations as AI tools through the core
 * {@link IAiToolInvoker} seam, backed by the {@code nopGraphQLToolSet} bean.
 *
 * <p>Migrated from the deprecated legacy tool-set contract (roadmap WI1;
 * P2-MA3-03 ruling): the GraphQL RPC invocation body carried over 1:1. The
 * final unification with the toolkit {@code IToolManager} runtime is adjudicated
 * by roadmap WI2. When {@code toolNames} is configured (config key
 * {@code nop.ai.tools.graphql-tool-names}), only those operations are resolvable.
 */
public class GraphQLToolProvider implements IAiToolInvoker {
    private final Map<String, ChatToolDefinition> cache = new ConcurrentHashMap<>();

    private final IGraphQLEngine graphqlEngine;
    private final Set<String> toolNames;

    public GraphQLToolProvider(IGraphQLEngine graphqlEngine) {
        this(graphqlEngine, null);
    }

    public GraphQLToolProvider(IGraphQLEngine graphqlEngine, Set<String> toolNames) {
        this.graphqlEngine = graphqlEngine;
        // null = unrestricted (all schema operations); an empty set allows none —
        // the unconfigured nopGraphQLToolSet must expose no tools (fail-safe).
        this.toolNames = toolNames;
    }

    /**
     * The configured operation-name filter; null means every schema operation
     * resolves, an empty set means none do.
     */
    public Set<String> configuredToolNames() {
        return toolNames;
    }

    @Override
    public ChatToolDefinition findTool(String toolName) {
        if (toolNames != null && !toolNames.contains(toolName))
            return null;
        return cache.computeIfAbsent(toolName, this::buildTool);
    }

    @Override
    public CompletionStage<Object> callTool(String toolName, Map<String, Object> args) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(args);
        IGraphQLExecutionContext ctx = graphqlEngine.newRpcContext(null, toolName, request);
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

    Set<String> listOperationNamesByPrefix(String prefix) {
        List<GraphQLFieldDefinition> opList = graphqlEngine.getSchemaLoader().getOperationDefinitions(null);
        Set<String> names = new LinkedHashSet<>();
        List<String> prefixList = ConvertHelper.toCsvList(prefix, NopException::new);

        opList.forEach(op -> {
            for (String prefixItem : prefixList) {
                if (op.getName().startsWith(prefixItem))
                    names.add(op.getName());
            }
        });
        return names;
    }

    protected ChatToolDefinition buildTool(String toolName) {
        GraphQLFieldDefinition op = graphqlEngine.getOperationDefinition(null, toolName);
        ChatToolDefinition tool = new ChatToolDefinition();
        tool.setName(toolName);
        tool.setDescription(op.getDescription());
        tool.setParameters(JsonTool.parseMap(GraphQLToJsonSchema.INSTANCE.argsToJsonSchema(op.getArguments()).toJson()));
        return tool;
    }
}
