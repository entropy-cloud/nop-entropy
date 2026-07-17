package io.nop.gateway;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;

public class GatewayRejectException extends RuntimeException {
    private final ApiResponse<?> rejectionResponse;

    public GatewayRejectException(ApiResponse<?> rejectionResponse) {
        super("Gateway rejected request: " + rejectionResponse.getHttpStatus());
        this.rejectionResponse = rejectionResponse;
    }

    public ApiResponse<?> getRejectionResponse() {
        return rejectionResponse;
    }
}
