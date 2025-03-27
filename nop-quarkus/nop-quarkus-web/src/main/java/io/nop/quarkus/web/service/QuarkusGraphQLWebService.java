/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.service;

import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.web.GraphQLWebService;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.quarkus.web.utils.QuarkusExecutorHelper;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_ARGS;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_SELECTION;
import static io.nop.quarkus.web.utils.QuarkusExecutorHelper.withRoutingContext;

@Path("")
@ApplicationScoped
public class QuarkusGraphQLWebService extends GraphQLWebService {
    // static final Logger LOG = LoggerFactory.getLogger(QuarkusGraphQLWebService.class);


    @POST
    @Path("/px/{serviceName}/{serviceMethod}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public CompletionStage<Response> proxy(@Context RoutingContext routingContext,
                                           @PathParam("serviceName") String serviceName,
                                           @PathParam("serviceMethod") String serviceMethod,
                                           @QueryParam(SYS_PARAM_SELECTION) String selection, String body) {
        return withRoutingContext(routingContext, () -> {
            return runProxy(serviceName, serviceMethod, () -> {
                return buildRequest(body, selection, true);
            }, this::buildJaxrsRestResponse);
        });
    }

    @POST
    @Path("/graphql")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public CompletionStage<Response> graphql(@Context RoutingContext routingContext, String body) {
        return withRoutingContext(routingContext, () -> runGraphQL(body, this::buildJaxrsGraphQLResponse));
    }

    @POST
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public CompletionStage<Response> rest(@Context RoutingContext routingContext,
                                          @PathParam("operationName") String operationName,
                                          @QueryParam(SYS_PARAM_SELECTION) String selection, String body) {
        return withRoutingContext(routingContext, () ->
                runRest(null, operationName, () -> {
                    return buildRequest(body, selection, true);
                }, this::buildJaxrsRestResponse));
    }

    @GET
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public CompletionStage<Response> restQuery(@Context RoutingContext routingContext,
                                               @PathParam("operationName") String operationName,
                                               @QueryParam(SYS_PARAM_SELECTION) String selection, @QueryParam(SYS_PARAM_ARGS) String args) {
        return withRoutingContext(routingContext, () ->
                runRest(GraphQLOperationType.query, operationName, () -> {
                    return buildRequest(args, selection, true);
                }, this::buildJaxrsRestResponse));
    }

    @GET
    @Path("/p/{query: [a-zA-Z].*}")
    public CompletionStage<Response> pageQueryGet(@Context RoutingContext routingContext, @PathParam("query") String query,
                                                  @QueryParam(SYS_PARAM_SELECTION) String selection,
                                                  @QueryParam(SYS_PARAM_ARGS) String args) {
        return withRoutingContext(routingContext, () ->
                doPageQuery(GraphQLOperationType.query, query, selection, args, this::buildJaxrsPageResponse));
    }

    @POST
    @Path("/p/{query: [a-zA-Z].*}")
    public CompletionStage<Response> pageQuery(@Context RoutingContext routingContext,
                                               @PathParam("query") String query,
                                               @QueryParam(SYS_PARAM_SELECTION) String selection, String body) {
        return withRoutingContext(routingContext, () ->
                doPageQuery(null, query, selection, body, this::buildJaxrsPageResponse));
    }


    @Override
    protected Map<String, String> getParams() {
        IHttpServerContext sc = QuarkusExecutorHelper.getHttpServerContext();
        if (sc == null)
            return Collections.emptyMap();
        return sc.getQueryParams();
    }

    @Override
    protected Map<String, Object> getHeaders() {
        Map<String, Object> ret = new TreeMap<>();
        IHttpServerContext sc = QuarkusExecutorHelper.getHttpServerContext();
        if (sc == null)
            return Collections.emptyMap();

        sc.getRequestHeaders().forEach((name, value) -> {
            name = name.toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name))
                return;
            ret.put(name, value);
        });
        return ret;
    }
}