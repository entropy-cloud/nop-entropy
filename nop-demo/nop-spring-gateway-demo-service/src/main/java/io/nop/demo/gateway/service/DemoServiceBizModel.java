/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.gateway.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.TccContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@BizModel("DemoService")
public class DemoServiceBizModel {

    private static final Logger LOG = LoggerFactory.getLogger(DemoServiceBizModel.class);

    private static final Map<String, String> RESOURCE_STORE = new ConcurrentHashMap<>();

    @BizMutation
    public Map<String, Object> testTcc(IServiceContext context) {
        TccContext tccCtx = TccContext.buildFromServiceContext(context);
        String txnId = tccCtx != null ? tccCtx.getTxnId() : null;
        String branchId = tccCtx != null ? tccCtx.getBranchId() : null;
        String resourceId = generateResourceId(txnId, branchId);

        LOG.info("DemoService.testTcc called: txnId={}, branchId={}, resourceId={}",
                txnId, branchId, resourceId);

        String currentState = RESOURCE_STORE.get(resourceId);
        if ("reserved".equals(currentState)) {
            LOG.warn("Resource already reserved: resourceId={}", resourceId);
            throw new IllegalStateException("Resource is already reserved: " + resourceId);
        }

        RESOURCE_STORE.put(resourceId, "reserved");

        Map<String, Object> result = new HashMap<>();
        result.put("resourceId", resourceId);
        result.put("status", "reserved");
        result.put("txnId", txnId);
        result.put("branchId", branchId);
        result.put("message", "Resource reserved successfully");

        LOG.info("Resource reserved successfully: resourceId={}", resourceId);
        return result;
    }

    @BizMutation
    public Map<String, Object> cancelTestTcc(IServiceContext context) {
        TccContext tccCtx = TccContext.buildFromServiceContext(context);
        String txnId = tccCtx != null ? tccCtx.getTxnId() : null;
        String branchId = tccCtx != null ? tccCtx.getBranchId() : null;
        String resourceId = generateResourceId(txnId, branchId);

        LOG.info("DemoService.cancelTestTcc called: txnId={}, branchId={}, resourceId={}",
                txnId, branchId, resourceId);

        String previousState = RESOURCE_STORE.remove(resourceId);
        if (previousState == null) {
            LOG.warn("Resource not found for cancellation: resourceId={}", resourceId);
        } else {
            LOG.info("Resource cancelled successfully: resourceId={}, previousState={}",
                    resourceId, previousState);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("resourceId", resourceId);
        result.put("status", "cancelled");
        result.put("txnId", txnId);
        result.put("branchId", branchId);
        result.put("message", "Resource cancelled successfully");

        return result;
    }

    @BizQuery
    public Map<String, Object> test(@Name("message") String message) {
        LOG.info("DemoService.test called: message={}", message);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Hello from DemoService: " + (message != null ? message : "no message"));
        result.put("timestamp", System.currentTimeMillis());
        result.put("status", "success");

        return result;
    }

    private String generateResourceId(String txnId, String branchId) {
        if (txnId == null) txnId = "default";
        if (branchId == null) branchId = "default";
        return txnId + ":" + branchId;
    }
}
