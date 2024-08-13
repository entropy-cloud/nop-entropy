package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancelToken;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface IDynamicRpcService {
    CompletionStage<ApiResponse<?>> dynamicInvokeAsync(ApiRequest<?> request, ICancelToken cancelToken);

    default CompletionStage<ApiResponse<?>> dynamicInvokeWithArgs(
            String serviceName, String serviceMethod, Map<String, Object> headers,
            Object data, FieldSelectionBean selection, ICancelToken cancelToken) {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(headers);
        request.setSelection(selection);
        request.setData(data);

        ApiHeaders.setSvcName(request, serviceName);
        ApiHeaders.setSvcAction(request, serviceMethod);
        return dynamicInvokeAsync(request, cancelToken);
    }
}
