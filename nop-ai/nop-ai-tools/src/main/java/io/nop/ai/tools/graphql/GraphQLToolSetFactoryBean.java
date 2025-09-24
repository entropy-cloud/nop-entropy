package io.nop.ai.tools.graphql;

import io.nop.ai.core.api.tool.DefaultAiChatToolSet;
import io.nop.ai.core.api.tool.IAiChatToolSet;
import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;

import java.util.Set;

public class GraphQLToolSetFactoryBean {
    private IGraphQLEngine graphQLEngine;
    private Set<String> toolNames;

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    @InjectValue("@cfg:nop.ai.tools.graphql-tool-names|")
    public void setToolNames(Set<String> toolNames) {
        this.toolNames = toolNames;
    }

    @BeanMethod
    public IAiChatToolSet buildToolSet() {
        if (CollectionHelper.isEmpty(toolNames))
            return new DefaultAiChatToolSet();
        GraphQLToolProvider provider = new GraphQLToolProvider(graphQLEngine);
        return provider.getToolSet(toolNames);
    }
}
