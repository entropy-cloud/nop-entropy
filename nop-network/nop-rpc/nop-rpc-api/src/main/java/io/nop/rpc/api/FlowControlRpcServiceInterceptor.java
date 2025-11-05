/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;

import java.util.concurrent.CompletionStage;

public class FlowControlRpcServiceInterceptor implements IRpcServiceInterceptor {
    private final IFlowControlRunner flowControlRunner;
    private final boolean inBound;

    public FlowControlRpcServiceInterceptor(IFlowControlRunner flowControlRunner, boolean inBound) {
        this.flowControlRunner = flowControlRunner;
        this.inBound = inBound;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        FlowControlEntry entry = newFlowControlEntry(inv);
        return flowControlRunner.runAsync(entry, inv::proceedAsync);
    }

    @Override
    public ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        FlowControlEntry entry = newFlowControlEntry(inv);
        return flowControlRunner.run(entry, inv::proceed);
    }

    protected FlowControlEntry newFlowControlEntry(IRpcServiceInvocation inv) {
        ApiRequest<?> req = inv.getRequest();
        FlowControlEntry entry = new FlowControlEntry();
        entry.setBizKey(ApiHeaders.getBizKey(req));
        entry.setOrigin(ApiHeaders.getAppId(req));
        entry.setInBound(inBound);
        entry.setResource(inv.getServiceName() + '/' + ApiHeaders.getSvcAction(req));
        return entry;
    }
}
