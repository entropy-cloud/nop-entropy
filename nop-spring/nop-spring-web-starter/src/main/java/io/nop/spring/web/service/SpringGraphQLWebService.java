/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.web.service;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.web.GraphQLWebService;
import io.nop.spring.core.resource.SpringResource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_ARGS;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_SELECTION;

@RestController
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
        return ret;
    }

    @Override
    protected Map<String, Object> getHeaders() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            throw new IllegalStateException("null request context");
        HttpServletRequest request = attrs.getRequest();
        Map<String, Object> ret = new TreeMap<>();
        Enumeration<String> it = request.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement().toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name))
                continue;
            ret.put(name, request.getHeader(name));
        }
        return ret;
    }


    @PostMapping(path = "/graphql", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<Object>> graphqlSpring(@RequestBody String body) {
        return runGraphQL(body, this::transformGraphQLResponse);
    }

    protected ResponseEntity<Object> transformGraphQLResponse(GraphQLResponseBean response, IGraphQLExecutionContext context) {
        HttpHeaders headers = new HttpHeaders();
        if (context != null && context.getResponseHeaders() != null) {
            context.getResponseHeaders().forEach((name, value) -> {
                List<String> list = Arrays.asList(String.valueOf(value));
                headers.put(name, list);
            });
        }
        Object body = JsonTool.serialize(response, false);
        ResponseEntity<Object> res = new ResponseEntity<>(body, headers, HttpStatus.OK);
        return res;
    }

    @PostMapping(path = "/r/{operationName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<Object>> restSpring(@PathVariable("operationName") String operationName,
                                                              @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
                                                              @RequestBody(required = false) String body) {
        return runRest(null, operationName, () -> {
            return buildRequest(body, selection, true);
        }, this::transformRestResponse);
    }

    @GetMapping(path = "/r/{operationName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<Object>> restQuerySpring(@PathVariable("operationName") String operationName,
                                                                   @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
                                                                   @RequestParam(value = SYS_PARAM_ARGS, required = false) String args) {
        return runRest(GraphQLOperationType.query, operationName, () -> {
            return buildRequest(args, selection, true);
        }, this::transformRestResponse);
    }

    protected ResponseEntity<Object> transformRestResponse(ApiResponse<?> response, IGraphQLExecutionContext context) {
        HttpHeaders headers = new HttpHeaders();
        if (response.getHeaders() != null) {
            response.getHeaders().forEach((name, value) -> {
                List<String> list = Arrays.asList(String.valueOf(value));
                headers.put(name, list);
            });
        }
        String str = JSON.stringify(response.cloneInstance(false));
        int status = response.getHttpStatus();
        if (status == 0)
            status = 200;

        ResponseEntity<Object> res = new ResponseEntity<>(str, headers, status);
        return res;
    }


    @GetMapping("/p/{query: [a-zA-Z].*}")
    @PostMapping("/p/{query: [a-zA-Z].*}")
    public CompletionStage<ResponseEntity<Object>> pageQuerySpring(@PathVariable("query") String query,
                                                                   @RequestParam(value = SYS_PARAM_SELECTION, required = false) String selection,
                                                                   @RequestParam(value = SYS_PARAM_ARGS, required = false) String args) {
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
        }, this::buildSpringPageResponse);
    }

    protected ResponseEntity<Object> buildSpringPageResponse(ApiResponse<?> res, IGraphQLExecutionContext context) {

        int status = res.getHttpStatus();
        if (status == 0)
            status = 200;

        HttpHeaders headers = new HttpHeaders();

        Object body;
        Object data = res.getData();
        if (data instanceof String) {
            headers.set(ApiConstants.HEADER_CONTENT_TYPE, WebContentBean.CONTENt_TYPE_TEXT);
            LOG.debug("nop.graphql.response:{}", data);
            body = data;
        } else if (data instanceof WebContentBean) {
            WebContentBean contentBean = (WebContentBean) data;
            body = buildContent(headers, contentBean.getContentType(), contentBean.getContent(), contentBean.getFileName());
        } else if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("contentType") && map.containsKey("content") && map.size() >= 2) {
                String contentType = ConvertHelper.toString(map.get("contentType"));
                body = buildContent(headers, contentType, map.get("content"), (String) map.get("fileName"));
            } else {
                body = buildJson(headers, res);
            }
        } else {
            body = buildJson(headers, res);
        }
        return new ResponseEntity<>(body, headers, HttpStatus.valueOf(status));
    }

    private Object buildContent(HttpHeaders headers, String contentType, Object content, String fileName) {
        headers.set(ApiConstants.HEADER_CONTENT_TYPE, contentType);
        if (content instanceof String) {
            LOG.debug("nop.graphql.response:{}", content);
            return content;
        } else if (content instanceof InputStream || content instanceof File || content instanceof IResource) {
            if (!StringHelper.isEmpty(fileName)) {
                String encoded = StringHelper.encodeURL(fileName);
                headers.set("Content-Disposition", "attachment; filename=" + encoded);
            }
            if (content instanceof InputStream) {
                return new InputStreamResource((InputStream) content, fileName);
            } else if (content instanceof IResource) {
                return new SpringResource((IResource) content);
            }
            return new FileSystemResource((File) content);
        } else {
            String str = JSON.stringify(content);
            LOG.debug("nop.graphql.response:{}", str);
            return str;
        }
    }

    private Object buildJson(HttpHeaders headers, ApiResponse<?> res) {
        headers.set(ApiConstants.HEADER_CONTENT_TYPE, WebContentBean.CONTENT_TYPE_JSON);
        String str;
        if (res.isOk()) {
            str = JSON.stringify(res.getData());
        } else {
            str = JSON.stringify(res.cloneInstance(false));
        }
        LOG.debug("nop.graphql.response:{}", str);
        return str;
    }
}