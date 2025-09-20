/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.graphql;

import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.ICancelToken;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import java.util.concurrent.CompletionStage;

/**
 * GraphQL服务的执行入口
 */
public interface GraphQLApi {
    @POST
    @Path("/graphql")
    @Consumes(MediaType.APPLICATION_JSON)
    CompletionStage<GraphQLResponseBean> invokeAsync(@RequestBean GraphQLRequestBean request);


    @POST
    @Path("/graphql")
    @Consumes(MediaType.APPLICATION_JSON)
    GraphQLResponseBean invoke(@RequestBean GraphQLRequestBean request);

    @POST
    @Path("/graphql")
    @Consumes(MediaType.APPLICATION_JSON)
    GraphQLResponseBean api_invoke(ApiRequest<GraphQLRequestBean> request, ICancelToken cancelToken);
}
