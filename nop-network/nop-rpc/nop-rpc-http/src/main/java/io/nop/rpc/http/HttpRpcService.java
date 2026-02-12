/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.http;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.IApiResponseNormalizer;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.HttpApiErrors;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.api.core.rpc.IRpcService;
import io.nop.rpc.core.utils.RpcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class HttpRpcService implements IRpcService {
    static final Logger LOG = LoggerFactory.getLogger(HttpRpcService.class);

    private final IHttpClient client;
    private final IRpcUrlBuilder urlBuilder;

    public HttpRpcService(IHttpClient client, IRpcUrlBuilder urlBuilder) {
        this.client = client;
        this.urlBuilder = urlBuilder;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        String url = urlBuilder.buildUrl(request, serviceMethod);
        LOG.info("nop.http.request:url={}", url);

        HttpRequest req = toHttpRequest(url, request);
        return client.fetchAsync(req, cancelToken).thenApply(res -> toApiResponse(res, request));
    }

    protected HttpRequest toHttpRequest(String url, ApiRequest<?> request) {
        HttpRequest req = new HttpRequest();
        req.setUrl(url);
        Map<String, Object> headers = new HashMap<>();

        if (request.hasHeaders()) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                name = urlBuilder.toHttpHeader(name);
                if (name != null)
                    headers.put(name, value);
            }
        }
        headers.put(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_JSON);

        String method = RpcHelper.getHttpMethod(request);
        if (method == null)
            method = HttpApiConstants.METHOD_POST;
        req.setMethod(method);
        req.setHeaders(headers);
        req.setBody(JSON.stringify(request.getData()));
        return req;
    }

    protected ApiResponse<?> toApiResponse(IHttpResponse response, ApiRequest<?> request) {
        int status = response.getHttpStatus();
        String text = response.getBodyAsText();

        IApiResponseNormalizer responseNormalizer = RpcHelper.getResponseNormalizer(request);

        ApiResponse<?> ret = null;
        if (responseNormalizer != null) {
            ret = responseNormalizer.toApiResponse(text);
        } else {
            try {
                boolean graphql = request.getData() instanceof GraphQLResponseBean;
                if (graphql) {
                    GraphQLResponseBean gql = (GraphQLResponseBean) JSON.parseToBean(null, text, GraphQLResponseBean.class, true, false);
                    if (gql != null)
                        ret = gql.toApiResponse();
                } else {
                    ret = (ApiResponse<?>) JSON.parseToBean(null, text, ApiResponse.class, true, false);
                }

            } catch (Exception e) {
                NopException.logIfNotTraced(LOG, "nop.err.http.response-not-json", e);

                ret = new ApiResponse<>();
                ret.setStatus(-status);
                ret.setCode(HttpApiErrors.ERR_HTTP_RESPONSE_FORMAT_NOT_EXPECTED.getErrorCode());
                ret.setMsg(text);
            }
        }

        if (ret == null) {
            ret = ApiResponse.success(null);
        }
        if(status != 200)
            ret.setHttpStatus(status);
        return ret;
    }
}
