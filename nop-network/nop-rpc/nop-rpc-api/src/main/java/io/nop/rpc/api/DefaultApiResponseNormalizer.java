/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.IApiResponseNormalizer;

public class DefaultApiResponseNormalizer implements IApiResponseNormalizer {
    public static DefaultApiResponseNormalizer INSTANCE = new DefaultApiResponseNormalizer();

    @Override
    public ApiResponse<?> toApiResponse(Object ret) {
        if (ret instanceof ApiResponse)
            return (ApiResponse<?>) ret;
        if (ret instanceof GraphQLResponseBean) {
            return ((GraphQLResponseBean) ret).toApiResponse();
        }
        ApiResponse<Object> res = ApiResponse.success(ret);
        res.setWrapper(true);
        return res;
    }
}
