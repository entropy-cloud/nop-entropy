/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.web.service;

import io.nop.graphql.core.web.GraphQLWebService;
import io.vertx.core.http.HttpServerRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.util.HashMap;
import java.util.Map;

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
}