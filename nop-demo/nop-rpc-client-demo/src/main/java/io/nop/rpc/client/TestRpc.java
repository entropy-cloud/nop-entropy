package io.nop.rpc.client;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;

public interface TestRpc {
    ApiResponse<String> myMethod(ApiRequest<MyRequest> req);
}
