package io.nop.api.core.rpc;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;

public class DefaultApiResponseNormalizer implements IApiResponseNormalizer {
    public static DefaultApiResponseNormalizer INSTANCE = new DefaultApiResponseNormalizer();

    @Override
    public ApiResponse<?> toApiResponse(Object ret) {
        if (ret instanceof ApiResponse)
            return (ApiResponse<?>) ret;
        if (ret instanceof GraphQLResponseBean) {
            return ((GraphQLResponseBean) ret).toApiResponse();
        }
        ApiResponse<Object> res = ApiResponse.buildSuccess(ret);
        res.setWrapper(true);
        return res;
    }
}
