package io.nop.code.api;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;

import java.util.List;
import java.util.Map;

@BizModel("NopCodeIndexApi")
public interface CodeIndexApi {

    @BizMutation
    ApiResponse<String> fullIndex(ApiRequest<Map<String, Object>> request);

    @BizQuery
    ApiResponse<List<Map<String, Object>>> searchCode(ApiRequest<Map<String, Object>> request);

    @BizQuery
    ApiResponse<Map<String, Object>> getOutline(ApiRequest<Map<String, Object>> request);

    @BizQuery
    ApiResponse<Map<String, Object>> getTypeHierarchy(ApiRequest<Map<String, Object>> request);

    @BizQuery
    ApiResponse<Map<String, Object>> getCallHierarchy(ApiRequest<Map<String, Object>> request);
}
