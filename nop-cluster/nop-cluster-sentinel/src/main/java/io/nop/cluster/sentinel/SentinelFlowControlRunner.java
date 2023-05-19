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
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
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

    @Override
    public <T> CompletionStage<T> runAsync(FlowControlEntry entry,
                                           Supplier<CompletionStage<T>> task) {
        ContextUtil.enter(entry.getContextName(), entry.getOrigin());

        Entry serviceEntry = null;
        // 务必保证 finally 会被执行

        EntryType entryType = entry.isInBound() ? EntryType.IN : EntryType.OUT;

        Object[] args = StringHelper.isEmpty(entry.getBizKey()) ? EMPTY_ARGS : new String[]{entry.getBizKey()};

        boolean proceedCalled = false;

        int resourceType = getResourceType(entry);

        try {
            // 资源名可使用任意有业务语义的字符串，注意数目不能太多（超过 1K），超出几千请作为参数传入而不要直接作为资源名
            // EntryType 代表流量类型（inbound/outbound），其中系统规则只对 IN 类型的埋点生效
            serviceEntry = SphU.asyncEntry(entry.getResource(), resourceType,
                    entryType, 1, args);

            // 被保护的业务逻辑
            CompletionStage<T> ret = task.get();
            proceedCalled = true;

            Entry serviceEntryArg = serviceEntry;
            return ret.whenComplete((r, e) -> {
                if (e != null) {
                    // 降级规则需要记录失败次数
                    if (serviceEntryArg != null)
                        Tracer.traceEntry(e, serviceEntryArg);
                }
                // 务必保证 exit，务必保证每个 entry 与 exit 配对
                if (serviceEntryArg != null) {
                    serviceEntryArg.exit(1, args);
                }
            });

        } catch (BlockException ex) {
            LOG.warn("nop.rpc.flow-control.block:contextName={},resourceName={},bizKey={}",
                    entry.getContextName(), entry.getResource(), entry.getBizKey());
            return FutureHelper.reject(newError(ex));
        } finally {
            if (!proceedCalled) {
                // 务必保证 exit，务必保证每个 entry 与 exit 配对
                if (serviceEntry != null) {
                    serviceEntry.exit(1, args);
                }
                // 如果当前curEntry不为null, 实际上不会真的退出
                ContextUtil.exit();
            }
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
