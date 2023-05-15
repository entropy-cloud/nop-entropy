package io.nop.api.core.rpc;

import io.nop.api.core.beans.ApiResponse;

public interface IApiResponseNormalizer {
    ApiResponse<?> toApiResponse(Object ret);
}
