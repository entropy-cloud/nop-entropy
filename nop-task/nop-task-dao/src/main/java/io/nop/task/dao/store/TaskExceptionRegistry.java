package io.nop.task.dao.store;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.task.exceptions.NopTaskCancelledException;
import io.nop.task.exceptions.NopTaskFailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * plan 266 Phase 1: nop-task-dao 内 exception 类型注册表（FQCN 字符串键 + 反射构造）。
 *
 * <p>设计裁定 1：registry 限定 nop-task-dao 模块内，以 <b>FQCN 字符串键</b> 注册 nop-task / nop-ai-agent
 * NopException 子类。{@code nop-task-dao} 的 pom 仅依赖 nop-api-core/nop-orm/nop-task-core，<b>不依赖
 * {@code nop-ai-agent}</b>（反向依赖会颠覆分层），故 nop-ai-agent 的 {@code NopAiAgentException} 等子类经
 * {@code Class.forName} 反射构造（无编译期依赖）。部署缺类（{@code ClassNotFoundException}）时该 FQCN
 * 安全回退 generic {@link NopException} 重构（向后兼容）。
 *
 * <p>注册集合经 live repo grep 确认（覆盖仍存在于 live repo 的 nop-task/nop-ai NopException 子类）：
 * <ul>
 *   <li>nop-task-core（编译期可用，工厂注册）：{@link NopTaskFailException}、{@link NopTaskCancelledException}</li>
 *   <li>nop-ai-agent / nop-ai-api（反射注册，部署缺类回退）：{@code NopAiAgentException}、{@code NopAiException}</li>
 * </ul>
 *
 * <p>构造精确子类时保留 errorCode + cause（params 由 {@code rebuildExceptionFromErrorBean} 统一恢复）。
 * 未注册 / 缺类 / 无合适构造器 / 历史 FQCN → 返回 null，调用方回退 generic {@link NopException}（#24 有日志不静默吞掉）。
 */
public final class TaskExceptionRegistry {

    static final Logger LOG = LoggerFactory.getLogger(TaskExceptionRegistry.class);

    /**
     * exception FQCN → 工厂。compile-time 工厂直接构造；reflective FQCN 经 lazy 反射解析后缓存。
     */
    private final Map<String, BiFunction<String, Throwable, NopException>> factories = new LinkedHashMap<>();

    /**
     * 全部已注册 FQCN（compile-time + reflective），供 {@link #getRegisteredFqcns()} grep 复核（Exit Criteria 可观测）。
     */
    private final Set<String> registeredFqcns = new LinkedHashSet<>();

    /**
     * 反射已解析失败的 FQCN 缓存（避免重复 {@code Class.forName}），value=true 表示已确认缺类/无构造器。
     */
    private final Set<String> reflectiveMissing = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public TaskExceptionRegistry() {
        // === compile-time 注册（nop-task-core 子类，编译期可用，工厂直接构造）===

        // NopTaskFailException: 仅 (ErrorCode,Throwable) / (ErrorCode) 构造器。errorCode 经 ErrorCode.define 从字符串恢复。
        registerFactory(NopTaskFailException.class.getName(),
                (errorCode, cause) -> new NopTaskFailException(ErrorCode.define(errorCode, null), cause));

        // NopTaskCancelledException: cancelReason 为 custom 字段（非 errorCode/params/cause 三件套），
        // 不经标准 ErrorBean 通路持久化。null reason 视为 kill（与 ICancellable.cancel() 默认一致，class javadoc）。
        registerFactory(NopTaskCancelledException.class.getName(),
                (errorCode, cause) -> NopTaskCancelledException.forReason(null, cause));

        // === reflective 注册（nop-ai 子类，部署可能缺类，lazy 反射解析）===

        registerReflective("io.nop.ai.agent.engine.NopAiAgentException");
        registerReflective("io.nop.ai.api.exceptions.NopAiException");
    }

    private void registerFactory(String fqcn, BiFunction<String, Throwable, NopException> factory) {
        factories.put(fqcn, factory);
        registeredFqcns.add(fqcn);
    }

    private void registerReflective(String fqcn) {
        registeredFqcns.add(fqcn);
        factories.put(fqcn, (errorCode, cause) -> resolveReflective(fqcn, errorCode, cause));
    }

    /**
     * 全部已注册 FQCN（不可变视图），供 grep / 测试复核。
     */
    public Set<String> getRegisteredFqcns() {
        return Collections.unmodifiableSet(registeredFqcns);
    }

    /**
     * consult registry 构造精确子类。
     *
     * @param fqcn      exception FQCN（从 errorBeanData 的 reserved param 读回）；null/未注册 → 返回 null
     * @param errorCode ErrorBean errorCode（字符串）
     * @param cause     已递归构造的 cause exception（可能为 null）
     * @return 精确子类实例；未注册 / 缺类 / 构造失败 → 返回 null（调用方回退 generic NopException）
     */
    public NopException create(String fqcn, String errorCode, Throwable cause) {
        if (fqcn == null || fqcn.isEmpty())
            return null;
        BiFunction<String, Throwable, NopException> factory = factories.get(fqcn);
        if (factory == null)
            return null; // 未注册 → 调用方回退
        if (reflectiveMissing.contains(fqcn))
            return null; // 已确认缺类/无构造器，避免重复反射
        try {
            NopException exp = factory.apply(errorCode, cause);
            if (exp != null)
                return exp;
            // factory 返回 null（反射解析失败）→ 标记 missing 并记录
            reflectiveMissing.add(fqcn);
            LOG.warn("nop.task.exception-registry-no-factory:fallback to generic NopException,fqcn={}", fqcn);
        } catch (Exception e) {
            reflectiveMissing.add(fqcn);
            LOG.warn("nop.task.exception-registry-create-failed:fallback to generic NopException,fqcn={}", fqcn, e);
        }
        return null;
    }

    /**
     * lazy 反射解析：{@code Class.forName} → 尝试 (String,Throwable) / (ErrorCode,Throwable) / (String) 构造器。
     * ClassNotFound / 无合适构造器 → 返回 null（#24 有日志）。
     *
     * <p>package-private 便于单元测试直接验证反射解析路径（覆盖 on-classpath 类的反射命中）。
     */
    NopException resolveReflective(String fqcn, String errorCode, Throwable cause) {
        Class<?> clazz;
        try {
            clazz = Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            // 部署缺类（nop-ai-agent 未部署）→ 安全回退
            LOG.warn("nop.task.exception-registry-class-not-found:fallback to generic NopException,fqcn={}", fqcn);
            return null;
        } catch (LinkageError | Exception e) {
            LOG.warn("nop.task.exception-registry-load-failed:fallback to generic NopException,fqcn={}", fqcn, e);
            return null;
        }
        if (!NopException.class.isAssignableFrom(clazz)) {
            LOG.warn("nop.task.exception-registry-not-nop-exception:fallback,fqcn={}", fqcn);
            return null;
        }

        // 1. (String, Throwable) —— NopAiAgentException / NopAiException 等字符串构造子类
        NopException exp = tryConstruct(clazz, new Class[]{String.class, Throwable.class}, errorCode, cause);
        if (exp != null)
            return exp;

        // 2. (ErrorCode, Throwable) —— ErrorCode 构造子类
        exp = tryConstruct(clazz, new Class[]{ErrorCode.class, Throwable.class},
                ErrorCode.define(errorCode, null), cause);
        if (exp != null)
            return exp;

        // 3. (String) 单参 —— 退化路径，cause 经 initCause 补
        exp = tryConstruct(clazz, new Class[]{String.class}, errorCode, null);
        if (exp != null) {
            if (cause != null)
                exp.initCause(cause);
            return exp;
        }

        // 4. (ErrorCode) 单参 —— 退化路径
        exp = tryConstruct(clazz, new Class[]{ErrorCode.class}, ErrorCode.define(errorCode, null), null);
        if (exp != null) {
            if (cause != null)
                exp.initCause(cause);
            return exp;
        }

        LOG.warn("nop.task.exception-registry-no-ctor:fallback to generic NopException,fqcn={}", fqcn);
        return null;
    }

    @SuppressWarnings("unchecked")
    private NopException tryConstruct(Class<?> clazz, Class<?>[] paramTypes, Object... args) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return (NopException) ctor.newInstance(args);
        } catch (NoSuchMethodException e) {
            return null; // 无此签名构造器，尝试下一个
        } catch (Exception e) {
            LOG.warn("nop.task.exception-registry-ctor-invoke-failed:fallback,fqcn={}", clazz.getName(), e);
            return null;
        }
    }
}
