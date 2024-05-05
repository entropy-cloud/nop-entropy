package io.nop.graphql.message;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class GraphQLMessageSubscription {
    private String messageSourceBean;
    private String topics;
}
