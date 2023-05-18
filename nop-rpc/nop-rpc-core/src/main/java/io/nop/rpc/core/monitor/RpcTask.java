/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.monitor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.rpc.api.IRpcService;

public class RpcTask {
    private String taskId;
    private IRpcService rpcService;
    private ApiRequest<?> request;
    private String statusMethod;
    private String cancelMethod;
    private int checkFailCount;

    public int getCheckFailCount() {
        return checkFailCount;
    }

    public void setCheckFailCount(int checkFailCount) {
        this.checkFailCount = checkFailCount;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public IRpcService getRpcService() {
        return rpcService;
    }

    public void setRpcService(IRpcService rpcService) {
        this.rpcService = rpcService;
    }

    public ApiRequest<?> getRequest() {
        return request;
    }

    public void setRequest(ApiRequest<?> request) {
        this.request = request;
    }

    public String getStatusMethod() {
        return statusMethod;
    }

    public void setStatusMethod(String statusMethod) {
        this.statusMethod = statusMethod;
    }

    public String getCancelMethod() {
        return cancelMethod;
    }

    public void setCancelMethod(String cancelMethod) {
        this.cancelMethod = cancelMethod;
    }
}
