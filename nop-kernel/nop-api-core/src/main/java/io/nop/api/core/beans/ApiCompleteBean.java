package io.nop.api.core.beans;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Api 调用结束可以将request和response信息发送到通知队列或者回调接口
 */
@DataBean
public class ApiCompleteBean<R,S> {
    private ApiRequest<R> request;
    private ApiResponse<S> response;

    public ApiResponse<S> getResponse() {
        return response;
    }

    public void setResponse(ApiResponse<S> response) {
        this.response = response;
    }

    public ApiRequest<R> getRequest() {
        return request;
    }

    public void setRequest(ApiRequest<R> request) {
        this.request = request;
    }
}
