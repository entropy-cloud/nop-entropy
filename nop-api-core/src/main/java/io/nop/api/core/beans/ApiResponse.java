/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.util.ApiHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 当成功返回时，status=0，一般仅data设置值
 * 当调用失败，需要返回异常时，status不为0，code和msg不为空。code为错误码，而msg为错误消息。
 * 返回消息结果与前台amis框架要求的基本保持一致
 */
@DataBean
public final class ApiResponse<T> extends ApiMessage {
    private static final long serialVersionUID = 7169855012236177821L;
    /**
     * httpStatus仅在后台创建http响应包时使用，它的信息不返回到前台
     */
    private int httpStatus;

    private int status;
    private String code;
    private String msg;
    private Integer msgTimeout;
    private Boolean bizFatal;

    private Map<String, String> errors;

    private T data;

    /**
     * maker checker对应的tryMethod的返回结果
     */
    private Object tryResponse;

    /**
     * 标记response的data才是实际返回的数据，ApiResponse完全是为了满足接口要求创造的一个包装对象
     */
    private boolean wrapper;

    public static <T> ApiResponse<T> buildSuccess(T data) {
        ApiResponse<T> ret = new ApiResponse<>();
        ret.setStatus(ApiConstants.API_STATUS_OK);
        ret.setData(data);
        return ret;
    }

    public static <T> ApiResponse<T> buildError(ErrorBean error) {
        ApiResponse<T> ret = new ApiResponse<>();
        ret.setStatus(ApiConstants.API_STATUS_FAIL);
        ret.setError(error);
        return ret;
    }

    public T get() {
        if (isOk()) {
            return getData();
        }
        throw NopRebuildException.rebuild(this);
    }

    @JsonIgnore
    public boolean isHttp2XX() {
        return this.httpStatus >= 200 && this.httpStatus < 300;
    }

    @JsonIgnore
    public boolean isHttp3XX() {
        return this.httpStatus >= 300 && this.httpStatus < 400;
    }

    @JsonIgnore
    public boolean isHttp4XX() {
        return this.httpStatus >= 400 && this.httpStatus < 500;
    }

    @JsonIgnore
    public boolean isHttp5XX() {
        return this.httpStatus >= 500 && this.httpStatus < 600;
    }

    @Override
    public ApiResponse<T> cloneInstance() {
        return cloneInstance(true);
    }

    @Override
    public ApiResponse<T> cloneInstance(boolean includeHeaders) {
        ApiResponse<T> ret = new ApiResponse<>();
        Map<String, Object> headers = getHeaders();
        if (headers != null) {
            headers = new TreeMap<>(headers);
        }
        ret.setHeaders(headers);
        ret.setHttpStatus(httpStatus);
        ret.setStatus(status);
        ret.setCode(code);
        ret.setMsg(msg);
        ret.setMsgTimeout(msgTimeout);
        ret.setErrors(errors);
        ret.setData(data);
        return ret;
    }

    @JsonInclude(Include.NON_DEFAULT)
    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @JsonIgnore
    public boolean isOk() {
        return status == 0;
    }

    @JsonIgnore
    public boolean isBizSuccess() {
        return isOk() && !ApiHeaders.isBizFail(this);
    }

    @JsonInclude(Include.NON_NULL)
    public Boolean getBizFatal() {
        return bizFatal;
    }

    public void setError(ErrorBean error) {
        if (error != null) {
            this.setStatus(error.getStatus());
            this.code = error.getErrorCode();
            this.msg = error.getDescription();
            this.bizFatal = error.isBizFatal();
            if (error.getDetails() != null) {
                this.errors = new LinkedHashMap<>();
                for (Map.Entry<String, ErrorBean> entry : error.getDetails().entrySet()) {
                    this.errors.put(entry.getKey(), entry.getValue().getDescription());
                }
            }
        } else {
            this.code = null;
            this.bizFatal = false;
            this.msg = null;
            this.errors = null;
        }
    }

    @JsonInclude(Include.NON_NULL)
    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @JsonInclude(Include.NON_NULL)
    public Integer getMsgTimeout() {
        return msgTimeout;
    }

    public void setMsgTimeout(Integer msgTimeout) {
        this.msgTimeout = msgTimeout;
    }

    public void setBizFatal(Boolean bizFatal) {
        this.bizFatal = bizFatal;
    }

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }

    @JsonInclude(Include.NON_NULL)
    public Object getTryResponse() {
        return tryResponse;
    }

    public void setTryResponse(Object tryResponse) {
        this.tryResponse = tryResponse;
    }

    @JsonIgnore
    public boolean isWrapper(){
        return wrapper;
    }

    public void setWrapper(boolean wrapper){
        this.wrapper = wrapper;
    }
}