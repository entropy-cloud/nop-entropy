/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.autoconfig.graphql;

import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Map;

@ConditionalOnClass({IGraphQLEngine.class, ResponseEntity.class})
@RestController
public class GraphQLController {
    @Autowired
    IGraphQLEngine graphQLEngine;

    // @POST
    // @Path("/graphql")
    // @Consumes(MediaType.APPLICATION_JSON)
    // @Produces(MediaType.APPLICATION_JSON)
    @PostMapping("/graphql")
    public ResponseEntity<GraphQLResponseBean> invokeAsync(@RequestBody GraphQLRequestBean req,
                                                           HttpServletRequest request) {
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(req);
        context.setRequestHeaders(getHttpHeaders(request));

        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        ResponseEntity<GraphQLResponseBean> ret = ResponseEntity.ok()
                .headers(toHttpHeaders(context.getResponseHeaders())).body(response);
        return ret;
    }

    HttpHeaders toHttpHeaders(Map<String, Object> headers) {
        HttpHeaders h = new HttpHeaders();
        if (headers != null) {
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                Object value = entry.getValue();
                h.set(entry.getKey(), String.valueOf(value));
            }
        }
        return h;
    }

    Map<String, Object> getHttpHeaders(HttpServletRequest request) {
        Map<String, Object> ret = new CaseInsensitiveMap<>();
        Enumeration<String> it = request.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            String value = request.getHeader(name);
            ret.put(name, value);
        }
        return ret;
    }
}