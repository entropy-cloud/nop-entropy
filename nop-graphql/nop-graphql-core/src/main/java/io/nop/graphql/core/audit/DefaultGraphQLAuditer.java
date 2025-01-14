package io.nop.graphql.core.audit;

import io.nop.graphql.core.IDataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultGraphQLAuditer implements IGraphQLAuditer {
    static final Logger LOG = LoggerFactory.getLogger(DefaultGraphQLAuditer.class);

    @Override
    public void beforeOperation(IDataFetchingEnvironment env) {

    }

    @Override
    public void afterOperation(IDataFetchingEnvironment env, Object result, Throwable exception) {
        Object args = env.getArgs();
        LOG.debug("nop.graphql.invoke-operation:operation={},args={}", env.getSelectionBean().getName(), args);
    }
}
