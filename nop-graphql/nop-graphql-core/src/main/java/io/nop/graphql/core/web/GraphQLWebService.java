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
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rpc.api.ContextBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public CompletionStage<Response> graphql(String body) {
        return runGraphQL(body, this::buildJaxrsGraphQLResponse);
    }

    protected <T> CompletionStage<T> runGraphQL(String body, BiFunction<GraphQLResponseBean,
            IGraphQLExecutionContext, T> responseBuilder) {
        IGraphQLEngine engine = BeanContainer.instance().getBeanByType(IGraphQLEngine.class);
        long beginTime = CoreMetrics.currentTimeMillis();

        ContextBinder binder = new ContextBinder();
        IGraphQLExecutionContext context = null;
        try {

            GraphQLRequestBean request = (GraphQLRequestBean) JSON.parseToBean(null, body, GraphQLRequestBean.class);
            LOG.debug("nop.graphql.parse:vars={},document=\n{}", request.getVariables(), request.getQuery());

            context = engine.newGraphQLContext(request);
            context.setMakerCheckerEnabled(CFG_GRAPHQL_MAKER_CHECKER_ENABLED.get());
            context.setRequestHeaders(getHeaders());
            prepareContext(context);

            ApiRequest<GraphQLRequestBean> req = ApiRequest.build(request);
            req.setHeaders(context.getRequestHeaders());
            binder.init(req);

            IGraphQLExecutionContext ctx = context;
            return engine.executeGraphQLAsync(context).thenApply(res -> {
                LOG.info("nop.graphql.end-graphql-request:usedTime={},query={},errorCode={},msg={}",
                        CoreMetrics.currentTimeMillis() - beginTime, request.getQuery(), res.getErrorCode(),
                        res.getMsg());
                return responseBuilder.apply(res, ctx);
            }).exceptionally(e -> {
                return responseBuilder.apply(engine.buildGraphQLResponse(null, e, ctx), ctx);
            }).whenComplete((v, e) -> binder.close());
        } catch (Exception e) {
            try {
                return FutureHelper.success(responseBuilder.apply(engine.buildGraphQLResponse(null, e, context), context));
            } finally {
                binder.close();
            }
        }
    }

    protected IGraphQLExecutionContext newGraphQLContext(IGraphQLEngine engine) {
        return engine.newGraphQLContext();
    }

    protected Response buildJaxrsGraphQLResponse(GraphQLResponseBean res, IGraphQLExecutionContext context) {
        Response.ResponseBuilder builder = Response.status(200);
        String str = JSON.stringify(res);
        LOG.debug("nop.graphql.response:{}", str);

        builder.entity(str);

        if (context != null && context.getResponseHeaders() != null) {
            context.getResponseHeaders().forEach(builder::header);
        }
        return builder.build();
    }

    @POST
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public CompletionStage<Response> rest(@PathParam("operationName") String operationName,
                                          @QueryParam(SYS_PARAM_SELECTION) String selection, String body) {
        return runRest(null, operationName, () -> {
            return buildRequest(body, selection, true);
        }, this::buildJaxrsRestResponse);
    }


    protected <T> CompletionStage<T> runRest(GraphQLOperationType expectedOpType, String operationName,
                                             Supplier<ApiRequest<?>> requestBuilder,
                                             BiFunction<ApiResponse<?>, IGraphQLExecutionContext, T> responseBuilder
    ) {
        IGraphQLEngine engine = BeanContainer.instance().getBeanByType(IGraphQLEngine.class);

        long beginTime = CoreMetrics.currentTimeMillis();

        ContextBinder binder = new ContextBinder();
        IGraphQLExecutionContext context = null;
        try {
            context = newGraphQLContext(engine);
            ApiRequest<?> request = requestBuilder.get();

            if (LOG.isDebugEnabled()) {
                LOG.debug("nop.graphql.rpc-request:operationName={},request={}", operationName,
                        JSON.serialize(request, true));
            }
            engine.initRpcContext(context, expectedOpType, operationName, request);
            context.setMakerCheckerEnabled(CFG_GRAPHQL_MAKER_CHECKER_ENABLED.get());
            prepareContext(context);

            binder.init(request);

            IGraphQLExecutionContext ctx = context;
            return engine.executeRpcAsync(context).thenApply(res -> {
                LOG.info("nop.graphql.end-rpc-request:usedTime={},operationName={},errorCode={},msg={}",
                        CoreMetrics.currentTimeMillis() - beginTime, operationName, res.getCode(), res.getMsg());
                return responseBuilder.apply(res, ctx);
            }).exceptionally(e -> {
                return responseBuilder.apply(engine.buildRpcResponse(null, e, ctx), ctx);
            }).whenComplete((r, e) -> binder.close());
        } catch (Exception e) {
            try {
                return FutureHelper.success(responseBuilder.apply(engine.buildRpcResponse(null, e, context), context));
            } finally {
                binder.close();
            }
        }
    }

    @GET
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public CompletionStage<Response> restQuery(@PathParam("operationName") String operationName,
                                               @QueryParam(SYS_PARAM_SELECTION) String selection, @QueryParam(SYS_PARAM_ARGS) String args) {
        return runRest(GraphQLOperationType.query, operationName, () -> {
            return buildRequest(args, selection, true);
        }, this::buildJaxrsRestResponse);
    }

    protected Response buildJaxrsRestResponse(ApiResponse<?> res, IGraphQLExecutionContext context) {
        Response response = JaxrsHelper.buildJaxrsResponse(res);
        LOG.debug("nop.graphql.response:{}", response.getEntity());
        return response;
    }


    protected void prepareContext(IGraphQLExecutionContext context) {

    }

    protected Map<String, String> getParams() {
        return Collections.emptyMap();
    }

    protected Map<String, Object> getHeaders() {
        return Collections.emptyMap();
    }

    static final Set<String> IGNORE_HEADERS = CollectionHelper.buildImmutableSet("connection",
            "accept", "accept-encoding", "content-length");

    protected boolean shouldIgnoreHeader(String name) {
        return IGNORE_HEADERS.contains(name);
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

        request.setHeaders(getHeaders());

        request.setData(map);
        if (!StringHelper.isEmpty(selection)) {
            request.setSelection(new FieldSelectionBeanParser().parseFromText(null, selection));
        }
        return request;
    }

    @POST
    @GET
    @Path("/p/{query: [a-zA-Z].*}")
    public CompletionStage<Response> pageQuery(@PathParam("query") String query,
                                               @QueryParam(SYS_PARAM_SELECTION) String selection, String body) {
        return doPageQuery(query, selection, body);
    }

    protected CompletionStage<Response> doPageQuery(@PathParam("query") String query,
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
        }, this::buildJaxrsPageResponse);
    }

    protected Response buildJaxrsPageResponse(ApiResponse<?> res, IGraphQLExecutionContext context) {

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
            buildContent(builder, contentBean.getContentType(), contentBean.getContent(), contentBean.getFileName());
        } else if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("contentType") && map.containsKey("content") && map.size() >= 2) {
                String contentType = ConvertHelper.toString(map.get("contentType"));
                buildContent(builder, contentType, map.get("content"), (String) map.get("fileName"));
            } else {
                buildJson(builder, res);
            }
        } else {
            buildJson(builder, res);
        }
        return builder.build();
    }

    protected void buildContent(Response.ResponseBuilder builder, String contentType, Object content, String fileName) {
        builder.header(ApiConstants.HEADER_CONTENT_TYPE, contentType);
        if (content instanceof String) {
            LOG.debug("nop.graphql.response:{}", content);
            builder.entity(content);
        } else if (content instanceof InputStream || content instanceof File) {
            builder.entity(content);
            if (!StringHelper.isEmpty(fileName)) {
                String encoded = StringHelper.encodeURL(fileName);
                builder.header("Content-Disposition", "attachment;filename=" + encoded);
            }
//            if(content instanceof File){
//                builder.header(HttpHeaders.CONTENT_LENGTH,((File) content).length());
//            }
        } else if (content instanceof IResource) {
            if (!StringHelper.isEmpty(fileName)) {
                String encoded = StringHelper.encodeURL(fileName);
                builder.header("Content-Disposition", "attachment;filename=" + encoded);
            }
            buildResourceContent(builder, (IResource) content);
        } else {
            String str = JSON.stringify(content);
            LOG.debug("nop.graphql.response:{}", str);
            builder.entity(str);
        }
    }

    protected void buildResourceContent(Response.ResponseBuilder builder, IResource content) {
        File file = content.toFile();
        if (file != null) {
            builder.entity(content);
        } else {
            builder.entity((StreamingOutput) content::writeToStream);
        }
    }

    private void buildJson(Response.ResponseBuilder builder, ApiResponse<?> res) {
        builder.header(ApiConstants.HEADER_CONTENT_TYPE, WebContentBean.CONTENT_TYPE_JSON + ";charset=UTF-8");
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