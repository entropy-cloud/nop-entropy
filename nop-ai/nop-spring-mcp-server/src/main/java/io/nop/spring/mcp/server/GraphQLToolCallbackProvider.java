package io.nop.spring.mcp.server;

import io.nop.ai.api.mcp.McpConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.biz.service.BizActionInvoker;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.schema.meta.GraphQLToJsonSchema;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class GraphQLToolCallbackProvider implements ToolCallbackProvider {
    private ToolCallback[] callbacks;

    @Override
    public ToolCallback[] getToolCallbacks() {
        if (callbacks == null)
            callbacks = buildCallbacks().toArray(new ToolCallback[0]);
        return callbacks;
    }

    public List<ToolCallback> buildCallbacks() {
        IBizObjectManager bizObjectManager = BeanContainer.getBeanByType(IBizObjectManager.class);
        IBizObject bizObj = bizObjectManager.getBizObject(McpConstants.BIZ_OBJ_AI_TOOL);
        List<ToolCallback> tools = new ArrayList<>();
        bizObj.getOperations().forEach((name, op) -> {
            tools.add(buildToolCallback(name, op));
        });
        return tools;
    }

    public ToolCallback buildToolCallback(String name, GraphQLFieldDefinition op) {
        FunctionToolCallback<Object, Object> callback = FunctionToolCallback.builder(name, getInvoker(name))
                .inputSchema(getInputSchema(op)).inputType(Map.class).description(op.getDescription()).build();
        return callback;
    }

    String getInputSchema(GraphQLFieldDefinition op) {
        return JsonTool.stringify(GraphQLToJsonSchema.INSTANCE.argsToJsonSchema(op.getArguments()));
    }

    public BiFunction<Object, ToolContext, Object> getInvoker(String name) {
        return (input, ctx) -> {
            ApiRequest<Object> req = ApiRequest.build(input);
            IServiceContext svcCtx = new ServiceContextImpl(ctx.getContext());
            return BizActionInvoker.invokeGraphQLSync(McpConstants.BIZ_OBJ_AI_TOOL, name, req, svcCtx);
        };
    }
}