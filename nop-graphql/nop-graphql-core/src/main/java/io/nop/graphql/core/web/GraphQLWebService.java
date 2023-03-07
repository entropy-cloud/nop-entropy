/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.web;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.json.JSON;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_MAKER_CHECKER_ENABLED;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_ARGS;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_SELECTION;

public class GraphQLWebService {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLWebService.class);

    public GraphQLWebService() {

    }

    @POST
    @Path("/graphql")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> graphql(String body) {

        IGraphQLEngine engine = BeanContainer.instance().getBeanByType(IGraphQLEngine.class);

        long beginTime = CoreMetrics.currentTimeMillis();

        try {
            GraphQLRequestBean request = (GraphQLRequestBean) JSON.parseToBean(null, body, GraphQLRequestBean.class);
            LOG.debug("nop.graphql.parse:vars={},document=\n{}", request.getVariables(), request.getQuery());

            IGraphQLExecutionContext context = engine.newGraphQLContext(request);
            context.setMakerCheckerEnabled(CFG_GRAPHQL_MAKER_CHECKER_ENABLED.get());
            prepareContext(context);

            return engine.executeGraphQLAsync(context).thenApply(res -> {
                LOG.info("nop.graphql.end-graphql-request:usedTime={},query={},errorCode={},msg={}",
                        CoreMetrics.currentTimeMillis() - beginTime, request.getQuery(), res.getErrorCode(),
                        res.getMsg());
                return buildGraphQLResponse(res, context);
            });
        } catch (Exception e) {
            return FutureHelper.success(JSON.stringify(engine.buildGraphQLResponse(null, e, null)));
        }
    }

    protected Response buildGraphQLResponse(GraphQLResponseBean res, IGraphQLExecutionContext context) {
        Response.ResponseBuilder builder = Response.status(200);
        String str = JSON.stringify(res);
        LOG.debug("nop.graphql.response:{}", str);

        builder.entity(str);

        if (context.getResponseHeaders() != null) {
            context.getResponseHeaders().forEach(builder::header);
        }
        return builder.build();
    }

    @POST
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> rest(@PathParam("operationName") String operationName,
                                          @QueryParam(SYS_PARAM_SELECTION) String selection, String body) {
        return runRest(null, operationName, () -> {
            return buildRequest(body, selection, true);
        });
    }

    protected CompletionStage<Response> runRest(GraphQLOperationType expectedOpType, String operationName,
                                                Supplier<ApiRequest<?>> requestBuilder) {
        return runRest(expectedOpType, operationName, requestBuilder, this::buildRestResponse);
    }

    protected CompletionStage<Response> runRest(GraphQLOperationType expectedOpType, String operationName,
                                                Supplier<ApiRequest<?>> requestBuilder,
                                                BiFunction<ApiResponse<?>, IGraphQLExecutionContext, Response> responseBuilder
    ) {
        IGraphQLEngine engine = BeanContainer.instance().getBeanByType(IGraphQLEngine.class);

        long beginTime = CoreMetrics.currentTimeMillis();

        try {
            ApiRequest<?> request = requestBuilder.get();
            if (LOG.isDebugEnabled()) {
                LOG.debug("nop.graphql.rpc-request:operationName={},request={}", operationName,
                        JSON.serialize(request, true));
            }
            IGraphQLExecutionContext context = engine.newRpcContext(expectedOpType, operationName, request);
            context.setMakerCheckerEnabled(CFG_GRAPHQL_MAKER_CHECKER_ENABLED.get());
            prepareContext(context);

            return engine.executeRpcAsync(context).thenApply(res -> {
                LOG.info("nop.graphql.end-rpc-request:usedTime={},operationName={},errorCode={},msg={}",
                        CoreMetrics.currentTimeMillis() - beginTime, operationName, res.getCode(), res.getMsg());
                return responseBuilder.apply(res, context);
            });
        } catch (Exception e) {
            return FutureHelper.success(JSON.stringify(engine.buildRpcResponse(null, e, null)));
        }
    }

    @GET
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> restQuery(@PathParam("operationName") String operationName,
                                               @QueryParam(SYS_PARAM_SELECTION) String selection, @QueryParam(SYS_PARAM_ARGS) String args) {
        return runRest(GraphQLOperationType.query, operationName, () -> {
            return buildRequest(args, selection, true);
        });
    }

    protected Response buildRestResponse(ApiResponse<?> res, IGraphQLExecutionContext context) {
        String str = JSON.stringify(res.cloneInstance(false));
        LOG.debug("nop.graphql.response:{}", str);

        int status = res.getHttpStatus();
        if (status == 0)
            status = 200;

        Response.ResponseBuilder builder = Response.status(status).entity(str);
        if (res.getHeaders() != null) {
            res.getHeaders().forEach(builder::header);
        }
        return builder.build();
    }


    protected void prepareContext(IGraphQLExecutionContext context) {

    }

    protected Map<String, String> getParams() {
        return Collections.emptyMap();
    }

    protected ApiRequest<Map<String, Object>> buildRequest(String body, String selection, boolean addParams) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> map = null;
        if (!StringHelper.isEmpty(body)) {
            map = (Map<String, Object>) JSON.parse(body);
        }
        if (map == null)
            map = new LinkedHashMap<>();

        if (addParams) {
            Map<String, String> params = getParams();
            for (String name : params.keySet()) {
                if (name.startsWith(GraphQLConstants.SYS_PARAM_PREFIX))
                    continue;
                map.put(name, params.get(name));
            }
        }

        request.setData(map);
        if (!StringHelper.isEmpty(selection)) {
            request.setFieldSelection(new FieldSelectionBeanParser().parseFromText(null, selection));
        }
        return request;
    }

    @GET
    @Path("/p/{query: [a-zA-Z].*}")
    public CompletionStage<Response> pageQuery(@PathParam("query") String query,
                                               @QueryParam(SYS_PARAM_SELECTION) String selection, @QueryParam(SYS_PARAM_ARGS) String args) {
        int pos = query.indexOf('/');
        String operationName = query;
        String path = pos > 0 ? query.substring(pos) : null;
        if (pos > 0) {
            operationName = query.substring(0, pos);
        }

        return runRest(GraphQLOperationType.query, operationName, () -> {
            ApiRequest<Map<String, Object>> req = buildRequest(args, selection, true);
            if (path != null) {
                req.getData().put(GraphQLConstants.PARAM_PATH, path);
            }
            return req;
        }, this::buildPageResponse);
    }

    protected Response buildPageResponse(ApiResponse<?> res, IGraphQLExecutionContext context) {

        int status = res.getHttpStatus();
        if (status == 0)
            status = 200;

        Response.ResponseBuilder builder = Response.status(status);
        if (res.getHeaders() != null) {
            res.getHeaders().forEach(builder::header);
        }

        Object data = res.getData();
        if (data instanceof String) {
            builder.header(ApiConstants.HEADER_CONTENT_TYPE, WebContentBean.CONTENt_TYPE_TEXT);
            LOG.debug("nop.graphql.response:{}", data);
            builder.entity(data);
        } else if (data instanceof WebContentBean) {
            WebContentBean contentBean = (WebContentBean) data;
            buildContent(builder, contentBean.getContentType(), contentBean.getContent());
        } else if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("contentType") && map.containsKey("content") && map.size() == 2) {
                String contentType = ConvertHelper.toString(map.get("contentType"));
                buildContent(builder, contentType, map.get("content"));
            } else {
                buildJson(builder, res);
            }
        } else {
            buildJson(builder, res);
        }
        return builder.build();
    }

    private void buildContent(Response.ResponseBuilder builder, String contentType, Object content) {
        builder.header(ApiConstants.HEADER_CONTENT_TYPE, contentType);
        if (content instanceof String) {
            LOG.debug("nop.graphql.response:{}", content);
            builder.entity(content);
        } else {
            String str = JSON.stringify(content);
            LOG.debug("nop.graphql.response:{}", str);
            builder.entity(str);
        }
    }

    private void buildJson(Response.ResponseBuilder builder, ApiResponse<?> res) {
        builder.header(ApiConstants.HEADER_CONTENT_TYPE, WebContentBean.CONTENT_TYPE_JSON);
        String str;
        if (res.isOk()) {
            str = JSON.stringify(res.getData());
        } else {
            str = JSON.stringify(res.cloneInstance(false));
        }
        LOG.debug("nop.graphql.response:{}", str);
        builder.entity(str);
    }
}