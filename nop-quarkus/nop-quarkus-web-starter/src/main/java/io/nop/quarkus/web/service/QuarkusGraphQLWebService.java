/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.service;

import io.nop.graphql.core.web.GraphQLWebService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Path("")
@ApplicationScoped
public class QuarkusGraphQLWebService extends GraphQLWebService {
    // static final Logger LOG = LoggerFactory.getLogger(QuarkusGraphQLWebService.class);

    @Context
    HttpServerRequest req;

    @Override
    protected Map<String, String> getParams() {
        Map<String, String> ret = new HashMap<>();
        req.params().forEach((name, value) -> {
            ret.put(name, value);
        });
        return ret;
    }

    @Override
    protected Map<String, Object> getHeaders() {
        Map<String, Object> ret = new TreeMap<>();
        req.headers().forEach((name, value) -> {
            name = name.toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name))
                return;
            ret.put(name, value);
        });
        return ret;
    }
}