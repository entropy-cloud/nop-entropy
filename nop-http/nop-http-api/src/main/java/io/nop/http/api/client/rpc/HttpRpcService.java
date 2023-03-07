/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.client.rpc;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.json.JSON;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class HttpRpcService implements IRpcService {
    static final Logger LOG = LoggerFactory.getLogger(HttpRpcService.class);

    private final IHttpClient client;
    private final Function<String, String> urlBuilder;

    public HttpRpcService(IHttpClient client, Function<String, String> urlBuilder) {
        this.client = client;
        this.urlBuilder = urlBuilder;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        String url = urlBuilder.apply(serviceMethod);

        HttpRequest req = toHttpRequest(url, request);
        return client.fetchAsync(req, cancelToken).thenApply(this::toApiResponse);
    }

    HttpRequest toHttpRequest(String url, ApiRequest<?> request) {
        request = request.cloneInstance();

        HttpRequest req = new HttpRequest();
        req.setUrl(url);
        Map<String, Object> headers = new HashMap<>();

        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (name.startsWith(ApiConstants.HTTP_HEADER_PREFIX)) {
                    name = name.substring(ApiConstants.HTTP_HEADER_PREFIX.length());
                    headers.put(name, value);
                }
            }
            if (!headers.isEmpty()) {
                request.getHeaders().keySet().removeAll(headers.keySet());
            }
        }
        headers.put(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_JSON);
        req.setHeaders(headers);
        req.setBody(JSON.stringify(request));
        return req;
    }

    ApiResponse<?> toApiResponse(IHttpResponse response) {
        int status = response.getHttpStatus();
        ApiResponse<?> ret;
        try {
            ret = (ApiResponse<?>) JSON.parseToBean(null, response.getBodyAsText(), ApiResponse.class, true, false);
        } catch (Exception e) {
            LOG.error("nop.err.http.response-not-json", e);

            // ret = new ApiResponse<>();
            // ret.setCode(HttpApiErrors.ERR_HTTP_RESPONSE_TEXT_NOT_JSON.getErrorCode());
            // ret.setMsg("RESPONSE TEXT NOT JSON");
            throw e;
        }
        ret.setHttpStatus(status);
        return ret;
    }
}
