/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.service;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.ApiHeaders;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.web.GraphQLWebService;
import io.nop.http.api.server.IClientIpFetcher;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.spring.core.resource.SpringResource;
import io.nop.spring.web.filter.ServletHttpServerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_ARGS;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_SELECTION;

@RestController
@ConditionalOnProperty(name = "nop.spring.graphql-web-service.enabled", havingValue = "true", matchIfMissing = true)
public class SpringGraphQLWebService extends GraphQLWebService {
    static final Logger LOG = LoggerFactory.getLogger(SpringGraphQLWebService.class);

    @Override
    protected Map<String, String> getParams() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            throw new IllegalStateException("null request context");
        HttpServletRequest request = attrs.getRequest();
        Map<String, String> ret = new HashMap<>();
        for (String paramName : request.getParameterMap().keySet()) {
            ret.put(paramName, request.getParameter(paramName));
        }
        if (attrs.getResponse() != null)
            attrs.getResponse().setCharacterEncoding("UTF-8");
        return ret;
    }

    @Override
    protected Map<String, Object> getHeaders() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            throw new IllegalStateException("null request context");
        HttpServletRequest request = attrs.getRequest();
        Map<String, Object> headers = SpringMvcHelper.getHeaders(request);
        IHttpServerContext serverCtx = new ServletHttpServerContext(attrs.getRequest(), attrs.getResponse());
        IClientIpFetcher clientIpFetcher = BeanContainer.getBeanByType(IClientIpFetcher.class);
        String clientAddr = clientIpFetcher.getClientRealAddr(serverCtx);
        ApiHeaders.setHeader(headers, ApiConstants.HEADER_CLIENT_ADDR, clientAddr);
        return headers;
    }

    @PostMapping(path = "/graphql", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<Object>> graphqlSpring(@RequestBody String body) {
        return runGraphQL(body, this::transformSpringResponse);
    }

    protected ResponseEntity<Object> transformSpringResponse(Map<String, Object> headers, Object body, int httpStatus) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach((name, value) -> {
            List<String> list = Collections.singletonList(String.valueOf(value));
            httpHeaders.put(name, list);
        });

        return new ResponseEntity<>(body, httpHeaders, httpStatus);
    }

    @PostMapping(path = "/px/{serviceName}/{serviceMethod}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<Object>> proxy(
            @PathVariable("serviceName") String serviceName,
            @PathVariable("serviceMethod") String serviceMethod,
            @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
            @RequestBody(required = false) String body) {
        return runProxy(serviceName, serviceMethod, () -> {
            return buildRequest(body, selection, true);
        }, this::transformSpringResponse);
    }

    @PostMapping(path = "/r/{operationName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<Object>> restSpring(@PathVariable("operationName") String operationName,
                                                              @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
                                                              @RequestBody(required = false) String body) {
        return runRest(null, operationName, () -> {
            return buildRequest(body, selection, true);
        }, this::transformSpringResponse);
    }

    @GetMapping(path = "/r/{operationName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<Object>> restQuerySpring(@PathVariable("operationName") String operationName,
                                                                   @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
                                                                   @RequestParam(value = SYS_PARAM_ARGS, required = false) String args) {
        return runRest(GraphQLOperationType.query, operationName, () -> {
            return buildRequest(args, selection, true);
        }, this::transformSpringResponse);
    }

    @GetMapping("/p/{query:.+}")
    public CompletionStage<ResponseEntity<Object>> pageQuerySpring(@PathVariable("query") String query,
                                                                   @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
                                                                   @RequestParam(value = SYS_PARAM_ARGS, required = false) String args) {
        return doPageQuery(GraphQLOperationType.query, query, selection, args, this::buildSpringPageResponse);
    }

    @PostMapping("/p/{query:.+}")
    public CompletionStage<ResponseEntity<Object>> pageQuerySpringForPost(@PathVariable("query") String query,
                                                                          @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
                                                                          @RequestBody(required = false) String body) {
        return doPageQuery(null, query, selection, body, this::buildSpringPageResponse);
    }

    protected ResponseEntity<Object> buildSpringPageResponse(
            ApiResponse<?> response, IGraphQLExecutionContext context
    ) {
        WebContentBean contentBean = buildWebContent(response);

        return consumeWebContent(response, contentBean, (headers, content, status) -> {
            if (content instanceof byte[]) {
                content = new ByteArrayResource((byte[]) content);
            } else if (content instanceof InputStream) {
                content = new InputStreamResource((InputStream) content, contentBean.getFileName());
            } else if (content instanceof IResource) {
                content = new SpringResource((IResource) content);
            } else if (content instanceof File) {
                content = new FileSystemResource((File) content);
            }

            return transformSpringResponse(headers, content, status);
        });
    }

}
