/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.graphql;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.utils.GraphQLRequestBuilder;
import io.nop.http.api.utils.HttpFetchTask;

import java.util.function.Function;
import java.util.function.Supplier;

public class GraphQLTask extends HttpFetchTask<Void> {
    private final GraphQLRequestBuilder graphqlRequest;

    public GraphQLTask(IHttpClient httpClient, Supplier<HttpRequest> requestSupplier, ICancelToken cancelToken,
                       GraphQLRequestBuilder graphqlRequest) {
        super(httpClient, adaptRequest(requestSupplier, graphqlRequest), cancelToken, adaptResponse(graphqlRequest));
        this.graphqlRequest = graphqlRequest;
    }

    public GraphQLRequestBuilder getRequest() {
        return graphqlRequest;
    }

    private static Supplier<HttpRequest> adaptRequest(Supplier<HttpRequest> supplier, GraphQLRequestBuilder graphqlRequest) {
        return () -> {
            HttpRequest req = supplier.get();
            req.setBody(graphqlRequest.build());
            return req;
        };
    }

    private static Function<IHttpResponse, Void> adaptResponse(GraphQLRequestBuilder graphqlRequest) {
        return res -> {
            graphqlRequest.handleResponse(res);
            return null;
        };
    }
}