/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.monitor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.rpc.IRpcService;

import java.util.concurrent.atomic.AtomicInteger;

public class RpcTask {
    private String taskId;
    private IRpcService rpcService;
    private ApiRequest<?> request;
    private String statusMethod;
    private String cancelMethod;
    // 4.3-MA-13（plan 2560 裁定 F）：checkFailCount 为跨 monitor 轮次并发递增/清零的
    // RMW 面（原 plain int 的 read-modify-write 丢失更新），改用 AtomicInteger 承载原子性
    private final AtomicInteger checkFailCount = new AtomicInteger();

    public int getCheckFailCount() {
        return checkFailCount.get();
    }

    public void setCheckFailCount(int checkFailCount) {
        this.checkFailCount.set(checkFailCount);
    }

    // 4.3-MA-13（plan 2560 裁定 F）：原子 RMW 单点——失败计数递增不再经历 read-then-write 窗口
    public int incrementCheckFailCount() {
        return checkFailCount.incrementAndGet();
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
