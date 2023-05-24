package io.nop.rpc.client;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.rpc.RpcMethod;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    @RpcMethod(cancelMethod = "Sys__cancel")
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req, ICancelToken cancelToken);

    @BizMutation
    @RpcMethod(cancelMethod = "Sys__cancel")
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(ApiRequest<MyRequest> req, ICancelToken cancelToken);
}
