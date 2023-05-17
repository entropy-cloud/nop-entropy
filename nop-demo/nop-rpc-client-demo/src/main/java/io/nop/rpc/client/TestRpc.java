package io.nop.rpc.client;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;

@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req);
}
