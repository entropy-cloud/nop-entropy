/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.makerchecker;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;

import java.time.LocalDateTime;

@DataBean
public class SendForCheckRequest {
    private String bizObjName;
    private String bizMethod;
    private String tryMethod;
    private String cancelMethod;

    private ApiRequest<?> request;
    /**
     * tryMethod的返回结果
     */
    private ApiResponse<?> tryResult;

    private String makerName;
    private String makerId;
    private LocalDateTime makeTime;

    public String getBizObjName() {
        return bizObjName;
    }

    public void setBizObjName(String bizObjName) {
        this.bizObjName = bizObjName;
    }

    public String getBizMethod() {
        return bizMethod;
    }

    public void setBizMethod(String bizMethod) {
        this.bizMethod = bizMethod;
    }

    public String getTryMethod() {
        return tryMethod;
    }

    public void setTryMethod(String tryMethod) {
        this.tryMethod = tryMethod;
    }

    public String getCancelMethod() {
        return cancelMethod;
    }

    public void setCancelMethod(String cancelMethod) {
        this.cancelMethod = cancelMethod;
    }

    public ApiRequest<?> getRequest() {
        return request;
    }

    public void setRequest(ApiRequest<?> request) {
        this.request = request;
    }

    public ApiResponse<?> getTryResult() {
        return tryResult;
    }

    public void setTryResult(ApiResponse<?> tryResult) {
        this.tryResult = tryResult;
    }

    public String getMakerName() {
        return makerName;
    }

    public void setMakerName(String makerName) {
        this.makerName = makerName;
    }

    public String getMakerId() {
        return makerId;
    }

    public void setMakerId(String makerId) {
        this.makerId = makerId;
    }

    public LocalDateTime getMakeTime() {
        return makeTime;
    }

    public void setMakeTime(LocalDateTime makeTime) {
        this.makeTime = makeTime;
    }
}