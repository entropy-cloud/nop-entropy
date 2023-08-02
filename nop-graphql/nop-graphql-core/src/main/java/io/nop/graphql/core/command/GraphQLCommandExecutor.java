package io.nop.graphql.core.command;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.json.JSON;
import io.nop.core.command.ICommandExecutor;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GraphQLCommandExecutor implements ICommandExecutor {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLCommandExecutor.class);

    private IGraphQLEngine graphQLEngine;

    private Set<String> allowedCommands;

    public void setAllowedCommands(Set<String> allowedCommands) {
        this.allowedCommands = allowedCommands;
    }

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    @Override
    public int execute(String command, Map<String, Object> params) {
        if (allowedCommands == null || !allowedCommands.contains(command)) {
            LOG.info("nop.graphql.not-allowed-command:command={}", command);
            return -404;
        }

        if (params == null) {
            params = new HashMap<>();
        }

        ApiRequest<Object> request = buildRequest(params);
        IGraphQLExecutionContext graphqlContext = graphQLEngine.newRpcContext(null, command, request);
        ApiResponse<?> response = graphQLEngine.executeRpc(graphqlContext);
        System.out.println(JSON.stringify(response));
        return response.getStatus();
    }

    private ApiRequest<Object> buildRequest(Map<String, Object> params) {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(params);
        return request;
    }
}
