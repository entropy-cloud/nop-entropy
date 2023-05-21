/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.utils;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.util.IApiResponseNormalizer;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.rpc.core.RpcConstants;

import java.time.Duration;

import static io.nop.rpc.core.RpcConfigs.CFG_RPC_CLIENT_EXT_DEFAULT_POLL_INTERVAL;

public class RpcHelper {
    public static ApiResponse<TaskStatusBean> toTaskStatusResponse(ApiResponse<?> response) {
        Object data = response.getData();
        if (data != null) {
            data = BeanTool.castBeanToType(response.getData(), TaskStatusBean.class);
            ((ApiResponse) response).setData(data);
        }
        return (ApiResponse<TaskStatusBean>) response;
    }

    public static String getCancelMethod(ApiRequest<?> request) {
        return request.getStringProperty(RpcConstants.PROP_CANCEL_METHOD);
    }

    public static void setCancelMethod(ApiRequest<?> request, String cancelMethod) {
        request.setProperty(RpcConstants.PROP_CANCEL_METHOD, cancelMethod);
    }

    public static String getPollingMethod(ApiRequest<?> request) {
        return request.getStringProperty(RpcConstants.PROP_POLLING_METHOD);
    }

    public static void setPollingMethod(ApiRequest<?> request, String pollingMethod) {
        request.setProperty(RpcConstants.PROP_POLLING_METHOD, pollingMethod);
    }

    public static int getPollInterval(ApiRequest<?> request) {
        Duration interval = CFG_RPC_CLIENT_EXT_DEFAULT_POLL_INTERVAL.get();
        int defaultValue = -1;
        if (interval != null)
            defaultValue = (int) interval.toMillis();
        return request.getIntProperty(RpcConstants.PROP_POLL_INTERVAL, defaultValue);
    }

    public static void setPollInterval(ApiRequest<?> request, int interval) {
        request.setProperty(RpcConstants.PROP_POLL_INTERVAL, interval);
    }

    public static IApiResponseNormalizer getResponseNormalizer(ApiRequest<?> request) {
        return (IApiResponseNormalizer) request.getProperty(RpcConstants.PROP_RESPONSE_NORMALIZER);
    }

    public static void setResponseNormalizer(ApiRequest<?> request, IApiResponseNormalizer normalizer) {
        request.setProperty(RpcConstants.PROP_RESPONSE_NORMALIZER, normalizer);
    }

    public static String getHttpMethod(ApiRequest<?> message) {
        return message.getStringProperty(RpcConstants.PROP_HTTP_METHOD);
    }

    public static void setHttpMethod(ApiRequest<?> message, String method) {
        message.setProperty(RpcConstants.PROP_HTTP_METHOD, method);
    }

    public static String getHttpUrl(ApiRequest<?> message) {
        return message.getStringProperty(RpcConstants.PROP_HTTP_URL);
    }

    public static void setHttpUrl(ApiRequest<?> message, String url) {
        message.setProperty(RpcConstants.PROP_HTTP_URL, url);
    }
}