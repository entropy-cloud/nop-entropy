package io.nop.ai.tools.graphql;

import io.nop.ai.core.command.IAiToolInvoker;
import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * IoC factory bean producing the {@code nopGraphQLToolSet} bean
 * ({@link IAiToolInvoker} over GraphQL operations).
 *
 * <p>Migrated from the deprecated legacy tool-set contract (roadmap WI1);
 * the bean id and conditional registration semantics are unchanged.
 */
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
    public IAiToolInvoker buildToolSet() {
        if (CollectionHelper.isEmpty(toolNames))
            return new GraphQLToolProvider(graphQLEngine, new java.util.LinkedHashSet<>());
        return new GraphQLToolProvider(graphQLEngine, toolNames);
    }
}
