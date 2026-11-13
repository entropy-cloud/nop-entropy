/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.ResourceTypeConstants;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static io.nop.rpc.api.RpcErrors.ARG_LIMIT_TYPE;
import static io.nop.rpc.api.RpcErrors.ARG_MSG;
import static io.nop.rpc.api.RpcErrors.ARG_RULE_LIMIT_APP;
import static io.nop.rpc.api.RpcErrors.ERR_RPC_FLOW_CONTROL_AUTHORITY;
import static io.nop.rpc.api.RpcErrors.ERR_RPC_FLOW_CONTROL_BLOCK;
import static io.nop.rpc.api.RpcErrors.ERR_RPC_FLOW_CONTROL_DEGRADE;
import static io.nop.rpc.api.RpcErrors.ERR_RPC_FLOW_CONTROL_SYS;

public class SentinelFlowControlRunner implements IFlowControlRunner {
    static final Logger LOG = LoggerFactory.getLogger(SentinelFlowControlRunner.class);

    static final Object[] EMPTY_ARGS = new Object[0];

    /**
     * 4.2-MA-05（plan 2559 裁定 B / C3-007）：异步包络兜底超时——底层 task stage 永不完成时
     * entry 永不 exit（泄漏面）。超时经模块配置约定键注入，<=0 关闭兜底（不推荐）。
     */
    @InjectValue("@cfg:nop.cluster.sentinel.task-timeout-ms|60000")
    protected long taskTimeoutMillis = 60000;

    public long getTaskTimeoutMillis() {
        return taskTimeoutMillis;
    }

    public void setTaskTimeoutMillis(long taskTimeoutMillis) {
        this.taskTimeoutMillis = taskTimeoutMillis;
    }

    @Override
    public <T> CompletionStage<T> runAsync(FlowControlEntry entry,
                                           Supplier<CompletionStage<T>> task) {
        ContextUtil.enter(entry.getContextName(), entry.getOrigin());

        Entry serviceEntry = null;
        // 务必保证 finally 会被执行

        EntryType entryType = entry.isInBound() ? EntryType.IN : EntryType.OUT;

        Object[] args = StringHelper.isEmpty(entry.getBizKey()) ? EMPTY_ARGS : new String[]{entry.getBizKey()};

        // 是否已进入异步段（异步段的 exit/trace 由 whenComplete + orTimeout 兜底负责）
        boolean asyncPhase = false;

        int resourceType = getResourceType(entry);

        try {
            // 资源名可使用任意有业务语义的字符串，注意数目不能太多（超过 1K），超出几千请作为参数传入而不要直接作为资源名
            // EntryType 代表流量类型（inbound/outbound），其中系统规则只对 IN 类型的埋点生效
            serviceEntry = SphU.asyncEntry(entry.getResource(), resourceType,
                    entryType, 1, args);

            // 被保护的业务逻辑
            // 4.2-MA-05（plan 2559 裁定 B / C3-008）：同步段异常（含 task supplier 同步抛出）
            // 统一包络为 failed future（FutureHelper.reject），不再裸抛打断调用方异步链。
            CompletionStage<T> ret;
            try {
                ret = task.get();
            } catch (Throwable e) {
                // 降级规则需要记录失败次数（C8-008：与 stage 异常完成路径统计对称）
                // Minor-11: SphU.asyncEntry 在 ContextUtil 未建立 context 时可返回 null
                //（Sentinel 1.8.6 CtSph.asyncEntryWithNoChain 的 null 返回路径），恢复 null 守卫
                if (serviceEntry != null) {
                    Tracer.traceEntry(e, serviceEntry);
                }
                return FutureHelper.reject(e);
            }
            asyncPhase = true;

            Entry serviceEntryArg = serviceEntry;
            // C8-008（plan 2559 裁定 B）：统一单次 exit——成功/异常完成在此退出；
            // orTimeout 兜底路径在下方超时回调退出（exit-once 守护防双重 exit）。
            AtomicBoolean exited = new AtomicBoolean();
            CompletableFuture<T> promise = new CompletableFuture<>();
            ret.whenComplete((r, e) -> {
                if (e != null && serviceEntryArg != null) {
                    // 降级规则需要记录失败次数
                    Tracer.traceEntry(e, serviceEntryArg);
                }
                if (serviceEntryArg != null && exited.compareAndSet(false, true)) {
                    // 务必保证 exit，务必保证每个 entry 与 exit 配对
                    serviceEntryArg.exit(1, args);
                }
                FutureHelper.complete(promise, r, e);
            });

            // C3-007（plan 2559 裁定 B）：orTimeout 兜底 exit——task stage 永不完成时
            // 上方 whenComplete 永不触发，entry 泄漏；超时后兜底完成 returned future
            // 并退出 entry。超时时长经 nop.cluster.sentinel.task-timeout-ms 配置。
            long timeout = taskTimeoutMillis;
            if (timeout > 0) {
                promise.<T>orTimeout(timeout, TimeUnit.MILLISECONDS).whenComplete((r, e) -> {
                    if (e instanceof TimeoutException) {
                        LOG.warn("nop.rpc.flow-control.task-timeout:contextName={},resourceName={},bizKey={},timeoutMs={}",
                                entry.getContextName(), entry.getResource(), entry.getBizKey(), timeout);
                        if (serviceEntryArg != null && exited.compareAndSet(false, true)) {
                            serviceEntryArg.exit(1, args);
                        }
                    }
                });
            }
            return promise;

        } catch (BlockException ex) {
            LOG.warn("nop.rpc.flow-control.block:contextName={},resourceName={},bizKey={}",
                    entry.getContextName(), entry.getResource(), entry.getBizKey());
            return FutureHelper.reject(newError(ex));
        } finally {
            if (!asyncPhase) {
                // 务必保证 exit，务必保证每个 entry 与 exit 配对
                if (serviceEntry != null) {
                    serviceEntry.exit(1, args);
                }
            }
            // P1-4.4-C3（plan 2395）：ContextUtil.enter/exit 必须无条件配对——成功路径
            // 此前的有条件 exit 永不执行，发起线程 context 栈持续
            // 增长（泄漏）。entry 的 exit 已在 whenComplete（成功）或上方（失败）配对；
            // context 属发起线程，须在本方法同步段内退出（对齐同步版 run() 的 finally 形态）。
            ContextUtil.exit();
        }
    }

    private RuntimeException newError(BlockException e) {
        if (e instanceof DegradeException) {
            DegradeException de = (DegradeException) e;
            return new NopException(ERR_RPC_FLOW_CONTROL_DEGRADE)
                    .param(ARG_RULE_LIMIT_APP, de.getRuleLimitApp())
                    .param(ARG_MSG, de.getMessage());
        } else if (e instanceof AuthorityException) {
            AuthorityException ae = (AuthorityException) e;
            return new NopException(ERR_RPC_FLOW_CONTROL_AUTHORITY)
                    .param(ARG_RULE_LIMIT_APP, ae.getRuleLimitApp())
                    .param(ARG_MSG, ae.getMessage());
        } else if (e instanceof SystemBlockException) {
            SystemBlockException se = (SystemBlockException) e;
            return new NopException(ERR_RPC_FLOW_CONTROL_SYS)
                    .param(ARG_RULE_LIMIT_APP, se.getRuleLimitApp())
                    .param(ARG_LIMIT_TYPE, se.getLimitType())
                    .param(ARG_MSG, se.getMessage());
        } else {
            return new NopException(ERR_RPC_FLOW_CONTROL_BLOCK)
                    .param(ARG_RULE_LIMIT_APP, e.getRuleLimitApp())
                    .param(ARG_MSG, e.getMessage());
        }
    }

    int getResourceType(FlowControlEntry entry) {
        if (entry.getResourceType() == null)
            return ResourceTypeConstants.COMMON;
        String type = entry.getResourceType();
        switch (type) {
            case FlowControlEntry.RESOURCE_TYPE_WEB:
                return ResourceTypeConstants.COMMON_WEB;
            case FlowControlEntry.RESOURCE_TYPE_RPC:
                return ResourceTypeConstants.COMMON_RPC;
            case FlowControlEntry.RESOURCE_TYPE_GATEWAY:
                return ResourceTypeConstants.COMMON_API_GATEWAY;
            case FlowControlEntry.RESOURCE_TYPE_SQL:
                return ResourceTypeConstants.COMMON_DB_SQL;
            default:
                return ResourceTypeConstants.COMMON;
        }
    }

    @Override
    public <T> T run(FlowControlEntry entry, Supplier<T> task) {
        ContextUtil.enter(entry.getContextName(), entry.getOrigin());

        Entry serviceEntry = null;
        // 务必保证 finally 会被执行

        EntryType entryType = entry.isInBound() ? EntryType.IN : EntryType.OUT;

        Object[] args = StringHelper.isEmpty(entry.getBizKey()) ? EMPTY_ARGS : new String[]{entry.getBizKey()};

        try {
            // 资源名可使用任意有业务语义的字符串，注意数目不能太多（超过 1K），超出几千请作为参数传入而不要直接作为资源名
            // EntryType 代表流量类型（inbound/outbound），其中系统规则只对 IN 类型的埋点生效
            serviceEntry = SphU.entry(entry.getResource(), getResourceType(entry), entryType, args);
            return task.get();
        } catch (BlockException ex) {
            LOG.warn("nop.rpc.flow-control.block:contextName={},resourceName={},bizKey={}",
                    entry.getContextName(), entry.getResource(), entry.getBizKey());
            throw newError(ex);
        } catch (Exception e) {
            // 降级规则需要记录失败次数
            if (serviceEntry != null)
                Tracer.traceEntry(e, serviceEntry);
            throw NopException.adapt(e);
        } finally {
            // 务必保证 exit，务必保证每个 entry 与 exit 配对
            if (serviceEntry != null) {
                serviceEntry.exit(1, args);
            }
            // 如果当前curEntry不为null, 实际上不会真的退出
            ContextUtil.exit();
        }
    }
}
