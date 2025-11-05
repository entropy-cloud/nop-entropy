package io.nop.graphql.core.jsonrpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.ApiHeaders;
import io.nop.core.model.selection.FieldSelectionBeanParser;

import java.util.HashMap;
import java.util.Map;

public class JsonRpcHelper {
    static final String KEY_PARAMS = "params";

    public static ApiRequest<Map<String, Object>> buildApiRequest(JsonRpcRequest request) {
        ApiRequest<Map<String, Object>> apiRequest = new ApiRequest<>();
        apiRequest.setHeaders(request.getMeta());
        if (request.getParams() instanceof Map) {
            apiRequest.setData((Map<String, Object>) request.getParams());
        } else if (request.getParams() != null) {
            Map<String, Object> map = new HashMap<>();
            map.put(KEY_PARAMS, request.getParams());
            apiRequest.setData(map);
        }
        ApiHeaders.setId(apiRequest, request.getId());

        if (request.getSelection() != null) {
            FieldSelectionBean selection = new FieldSelectionBeanParser().parseFromText(null, request.getSelection());
            apiRequest.setSelection(selection);
        }
        return apiRequest;
    }

    public static <T> JsonRpcResponse<T> buildJsonRpcResponse(ApiResponse<T> response, String id) {
        JsonRpcResponse<T> ret = new JsonRpcResponse<>();
        ret.setResult(response.getData());
        ret.setId(id);
        if (!response.isOk()) {
            JsonRpcResponse.Error error = new JsonRpcResponse.Error();
            error.setErrorCode(response.getCode());
            error.setCode(response.getStatus());
            error.setMessage(response.getMsg());
            ret.setError(error);
        }
        return ret;
    }
}
