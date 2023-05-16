package io.nop.rpc.client;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;

public interface EchoService {
    ApiResponse<String> echo(ApiRequest<String> request);
}