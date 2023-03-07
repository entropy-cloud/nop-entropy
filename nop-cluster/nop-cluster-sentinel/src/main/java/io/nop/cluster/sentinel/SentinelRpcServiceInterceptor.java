/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.ResourceTypeConstants;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class SentinelRpcServiceInterceptor
        implements IRpcServiceInterceptor {
    static final Logger LOG = LoggerFactory.getLogger(SentinelRpcServiceInterceptor.class);

    static final Object[] EMPTY_ARGS = new Object[0];

    private final int resourceType;
    private final String contextName;
    private final boolean useMethodResource;

    public SentinelRpcServiceInterceptor(String contextName, boolean methodResource) {
        this(contextName, methodResource, ResourceTypeConstants.COMMON);
    }

    public SentinelRpcServiceInterceptor(String contextName, boolean methodResource,
                                         int resourceType) {
        this.resourceType = resourceType;
        this.contextName = contextName;
        this.useMethodResource = methodResource;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        ContextUtil.enter(getContextName(inv), getOrigin(inv.getRequest()));

        Entry serviceEntry = null;
        Entry methodEntry = null;
        // 务必保证 finally 会被执行

        EntryType entryType = inv.isInbound() ? EntryType.IN : EntryType.OUT;

        Object[] args = getRequestArgs(inv.getRequest());

        boolean proceedCalled = false;

        try {
            // 资源名可使用任意有业务语义的字符串，注意数目不能太多（超过 1K），超出几千请作为参数传入而不要直接作为资源名
            // EntryType 代表流量类型（inbound/outbound），其中系统规则只对 IN 类型的埋点生效
            serviceEntry = SphU.asyncEntry(inv.getServiceName(), resourceType, entryType, 1, args);
            methodEntry = useMethodResource ? SphU.asyncEntry(inv.getServiceName() + "::" + inv.getServiceMethod(),
                    resourceType, entryType, 1, args) : null;

            // 被保护的业务逻辑
            CompletionStage<ApiResponse<?>> ret = inv.proceedAsync();
            proceedCalled = true;

            Entry serviceEntryArg = serviceEntry;
            Entry methodEntryArg = methodEntry;
            return ret.whenComplete((r, e) -> {
                if (e == null) {
                    if (r != null) {
                        if (isFailed(r)) {
                            e = buildException(r);
                        }
                    }
                }
                if (e != null) {
                    // 降级规则需要记录失败次数
                    if (serviceEntryArg != null)
                        Tracer.traceEntry(e, serviceEntryArg);
                    if (methodEntryArg != null)
                        Tracer.traceEntry(e, methodEntryArg);
                }
                // 务必保证 exit，务必保证每个 entry 与 exit 配对
                if (serviceEntryArg != null) {
                    serviceEntryArg.exit(1, args);
                }
                if (methodEntryArg != null) {
                    methodEntryArg.exit(1, args);
                }
            });

        } catch (BlockException ex) {
            LOG.warn("nop.service.sentinel-block:serviceName={},serviceAction={},reqId={}",
                    inv.getServiceName(), inv.getServiceMethod(), ApiHeaders.getId(inv.getRequest()));
            ApiResponse<?> response = ErrorMessageManager.instance().buildResponse(inv.getRequest(), ex);
            return FutureHelper.success(response);
        } finally {
            if (!proceedCalled) {
                // 务必保证 exit，务必保证每个 entry 与 exit 配对
                if (serviceEntry != null) {
                    serviceEntry.exit(1, args);
                }
                if (methodEntry != null) {
                    methodEntry.exit(1, args);
                }
                // 如果当前curEntry不为null, 实际上不会真的退出
                ContextUtil.exit();
            }
        }
    }

    @Override
    public ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        ContextUtil.enter(getContextName(inv), getOrigin(inv.getRequest()));

        Entry serviceEntry = null;
        Entry methodEntry = null;
        // 务必保证 finally 会被执行

        EntryType entryType = inv.isInbound() ? EntryType.IN : EntryType.OUT;

        Object[] args = getRequestArgs(inv.getRequest());

        try {
            // 资源名可使用任意有业务语义的字符串，注意数目不能太多（超过 1K），超出几千请作为参数传入而不要直接作为资源名
            // EntryType 代表流量类型（inbound/outbound），其中系统规则只对 IN 类型的埋点生效
            serviceEntry = SphU.entry(inv.getServiceName(), resourceType, entryType, args);
            methodEntry = useMethodResource ? SphU.entry(inv.getServiceName() + "::" + inv.getServiceMethod(),
                    resourceType, entryType, args) : null;

            // 被保护的业务逻辑
            ApiResponse<?> ret = inv.proceed();
            if (isFailed(ret)) {
                throw buildException(ret);
            }
            return ret;
        } catch (BlockException ex) {
            LOG.warn("nop.service.sentinel-block:serviceName={},serviceAction={},reqId={}",
                    inv.getServiceName(), inv.getServiceMethod(), ApiHeaders.getId(inv.getRequest()));
            ApiResponse<?> response = ErrorMessageManager.instance().buildResponse(inv.getRequest(), ex);
            return response;
        } catch (Exception e) {
            // 降级规则需要记录失败次数
            if (serviceEntry != null)
                Tracer.traceEntry(e, serviceEntry);
            if (methodEntry != null)
                Tracer.traceEntry(e, methodEntry);
            throw NopException.adapt(e);
        } finally {
            // 务必保证 exit，务必保证每个 entry 与 exit 配对
            if (serviceEntry != null) {
                serviceEntry.exit(1, args);
            }
            if (methodEntry != null) {
                methodEntry.exit(1, args);
            }
            // 如果当前curEntry不为null, 实际上不会真的退出
            ContextUtil.exit();
        }
    }

    protected String getContextName(IRpcServiceInvocation inv) {
        if (contextName == null || contextName.isEmpty())
            return inv.getServiceName();
        return contextName;
    }

    protected String getOrigin(ApiRequest<?> request) {
        return ApiHeaders.getAppId(request);
    }

    protected Object[] getRequestArgs(ApiRequest<?> request) {
        String bizKey = ApiHeaders.getBizKey(request);
        if (bizKey == null)
            return EMPTY_ARGS;
        return new Object[]{bizKey};
    }

    protected Exception buildException(ApiResponse<?> response) {
        return NopRebuildException.rebuild(response);
    }

    protected boolean isFailed(ApiResponse<?> response) {
        return !response.isOk() && response.isHttp5XX();
    }
}
