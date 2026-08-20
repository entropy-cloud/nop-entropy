/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.xlang.XLangErrors.ARG_BACKEND_ID;
import static io.nop.xlang.XLangErrors.ARG_CAPABILITY;
import static io.nop.xlang.XLangErrors.ERR_XLANG_BACKEND_ALREADY_REGISTERED;
import static io.nop.xlang.XLangErrors.ERR_XLANG_BACKEND_CAPABILITY_CONFLICT;
import static io.nop.xlang.XLangErrors.ERR_XLANG_BACKEND_INVALID_CONTRACT;

/**
 * 执行后端显式注册表（{@code ScriptCompilerRegistry} / {@code @GlobalInstance} 先例同款形态）。
 *
 * <p>注册时机 = 后端模块初始化代码显式注册（ICoreInitializer + META-INF/services，
 * {@code XLangCoreInitializer} 内 {@code JaninoScriptCompiler.register()} 先例）；查询不做
 * classpath 扫描。失败语义 = 初始化失败的后端以不可用条目注册（保留原因），不阻断启动。
 *
 * <p>能力槽位唯一性：STATIC_GENERATED 与 DYNAMIC_TRANSLATION 各占一个槽位（决策树两分支的
 * 判定对象），同能力槽位注册第二个不同后端 = 契约非法，fail-fast。
 */
@GlobalInstance
public class EvalBackendRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(EvalBackendRegistry.class);

    private static final EvalBackendRegistry _instance = new EvalBackendRegistry();

    public static EvalBackendRegistry instance() {
        return _instance;
    }

    private final ConcurrentHashMap<String, IEvalExecutionBackend> backends = new ConcurrentHashMap<>();

    /**
     * 显式注册后端。同 id 幂等重注册（同实例）；同 id 不同实例、能力槽位冲突、契约非法
     * （空标识/空能力集）均 fail-fast。
     */
    public synchronized void register(IEvalExecutionBackend backend) {
        if (backend == null)
            throw new NopException(ERR_XLANG_BACKEND_INVALID_CONTRACT).param(ARG_BACKEND_ID, "null");

        String id = backend.getBackendId();
        if (StringHelper.isEmpty(id))
            throw new NopException(ERR_XLANG_BACKEND_INVALID_CONTRACT).param(ARG_BACKEND_ID, "<empty>");

        Set<EvalBackendCapability> capabilities = backend.getCapabilities();
        if (capabilities == null || capabilities.isEmpty())
            throw new NopException(ERR_XLANG_BACKEND_INVALID_CONTRACT).param(ARG_BACKEND_ID, id);

        IEvalExecutionBackend existing = backends.get(id);
        if (existing != null && existing != backend)
            throw new NopException(ERR_XLANG_BACKEND_ALREADY_REGISTERED).param(ARG_BACKEND_ID, id);

        for (EvalBackendCapability capability : capabilities) {
            IEvalExecutionBackend slotOwner = findCapabilityOwner(capability);
            if (slotOwner != null && slotOwner != backend)
                throw new NopException(ERR_XLANG_BACKEND_CAPABILITY_CONFLICT)
                        .param(ARG_CAPABILITY, capability).param(ARG_BACKEND_ID, slotOwner.getBackendId());
        }

        if (existing == null) {
            backends.put(id, backend);
            if (!backend.isAvailable()) {
                LOG.warn("nop.xlang.execution.backend-registered-unavailable: backend={}, reason={}",
                        id, backend.getUnavailableReason());
            }
        }
    }

    /** 反注册（仅当该 id 当前映射到同一实例时移除，{@code remove(key, value)} 条件式先例同款） */
    public synchronized void unregister(IEvalExecutionBackend backend) {
        if (backend == null)
            return;
        backends.remove(backend.getBackendId(), backend);
    }

    public IEvalExecutionBackend getBackend(String backendId) {
        return backends.get(backendId);
    }

    public Set<String> getBackendIds() {
        return Collections.unmodifiableSet(backends.keySet());
    }

    public boolean isEmpty() {
        return backends.isEmpty();
    }

    /** 不可用条目诊断查询：backendId → 不可用原因（只含已注册但 isAvailable()==false 的条目） */
    public Map<String, String> getUnavailableBackends() {
        Map<String, String> result = new LinkedHashMap<>();
        backends.forEach((id, backend) -> {
            if (!backend.isAvailable())
                result.put(id, backend.getUnavailableReason());
        });
        return result;
    }

    /** 静态生成物能力槽位当前占用者（无则 null） */
    public IEvalStaticBackend findStaticBackend() {
        IEvalExecutionBackend owner = findCapabilityOwner(EvalBackendCapability.STATIC_GENERATED);
        return owner instanceof IEvalStaticBackend ? (IEvalStaticBackend) owner : null;
    }

    /** 动态翻译能力槽位当前占用者（无则 null） */
    public IEvalDynamicBackend findDynamicBackend() {
        IEvalExecutionBackend owner = findCapabilityOwner(EvalBackendCapability.DYNAMIC_TRANSLATION);
        return owner instanceof IEvalDynamicBackend ? (IEvalDynamicBackend) owner : null;
    }

    private IEvalExecutionBackend findCapabilityOwner(EvalBackendCapability capability) {
        for (IEvalExecutionBackend backend : backends.values()) {
            if (backend.getCapabilities().contains(capability))
                return backend;
        }
        return null;
    }
}
