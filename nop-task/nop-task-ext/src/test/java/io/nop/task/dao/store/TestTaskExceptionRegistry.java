package io.nop.task.dao.store;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.task.exceptions.NopTaskFailException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 266 Phase 1 focused 单元测试：验证 {@link TaskExceptionRegistry} 的 registry 命中 / 反射 ClassNotFound 回退 /
 * 未注册回退逻辑（非 DB-backed，直接测 registry.create / resolveReflective）。
 *
 * <p>覆盖 Exit Criteria：
 * <ul>
 *   <li>exception 类型注册表存在于 nop-task-dao，注册集合经 live repo grep 可复核（{@link #registeredFqcns_containsAllKnownSubclasses}）</li>
 *   <li>rebuild 经 registry 命中精确子类（{@link #create_compiledFactory_returnsPreciseSubclass}）</li>
 *   <li>反射命中 on-classpath 类（{@link #resolveReflective_onClasspathClass_returnsInstance}）</li>
 *   <li>反射 ClassNotFound 回退（{@link #create_reflectiveMissingClass_returnsNull}）</li>
 *   <li>未注册 FQCN 回退（{@link #create_unregisteredFqcn_returnsNull}）</li>
 *   <li>null/空 FQCN 回退（{@link #create_nullOrEmptyFqcn_returnsNull}）</li>
 * </ul>
 */
public class TestTaskExceptionRegistry {

    private final TaskExceptionRegistry registry = new TaskExceptionRegistry();

    static final ErrorCode ERR_TEST = ErrorCode.define("nop.err.test.registry", "test");

    // ==================== 注册集合可观测性（Exit Criteria: 注册集合经 live repo grep 可复核）====================

    /**
     * registry 注册集合包含全部已知 nop-task / nop-ai NopException 子类 FQCN（grep 可复核）。
     */
    @Test
    public void registeredFqcns_containsAllKnownSubclasses() {
        assertTrue(registry.getRegisteredFqcns().contains(NopTaskFailException.class.getName()),
                "registry must register NopTaskFailException (nop-task-core compile-time)");
        assertTrue(registry.getRegisteredFqcns().contains("io.nop.ai.agent.engine.NopAiAgentException"),
                "registry must register NopAiAgentException (nop-ai-agent reflective)");
        assertTrue(registry.getRegisteredFqcns().contains("io.nop.ai.api.exceptions.NopAiException"),
                "registry must register NopAiException (nop-ai-api reflective)");
    }

    // ==================== compile-time registry 命中 ====================

    /**
     * Exit Criteria: rebuild 经 registry 命中精确子类。
     * NopTaskFailException 经 compile-time 工厂构造，instanceof 成立 + errorCode 保留。
     */
    @Test
    public void create_compiledFactory_returnsPreciseSubclass() {
        NopException exp = registry.create(NopTaskFailException.class.getName(), ERR_TEST.getErrorCode(), null);
        assertNotNull(exp, "registry must return non-null for registered compiled FQCN");
        assertTrue(exp instanceof NopTaskFailException,
                "registry must return precise subclass instance (NopTaskFailException), got: " + exp.getClass().getName());
        assertEquals(ERR_TEST.getErrorCode(), exp.getErrorCode(),
                "errorCode must be preserved by registry factory");
    }

    /**
     * compile-time factory 保留 cause chain。
     */
    @Test
    public void create_compiledFactory_preservesCause() {
        Throwable cause = new IllegalStateException("root");
        NopException exp = registry.create(NopTaskFailException.class.getName(), ERR_TEST.getErrorCode(), cause);
        assertNotNull(exp);
        assertEquals(cause, exp.getCause(), "registry factory must preserve cause");
    }

    // ==================== 反射命中（on-classpath 类）====================

    /**
     * 接线验证（#23）：反射解析路径对 on-classpath 类成功构造。
     * NopTaskFailException 在 nop-task-core（test classpath），反射解析其 (ErrorCode,Throwable) 构造器。
     */
    @Test
    public void resolveReflective_onClasspathClass_returnsInstance() {
        NopException exp = registry.resolveReflective(
                NopTaskFailException.class.getName(), ERR_TEST.getErrorCode(), null);
        assertNotNull(exp, "reflective resolution must succeed for on-classpath class");
        assertTrue(exp instanceof NopTaskFailException,
                "reflective resolution must construct precise subclass, got: " + exp.getClass().getName());
        assertEquals(ERR_TEST.getErrorCode(), exp.getErrorCode());
    }

    /**
     * 反射解析保留 cause（经构造器传入或 initCause 补）。
     */
    @Test
    public void resolveReflective_preservesCause() {
        Throwable cause = new IllegalArgumentException("inner");
        NopException exp = registry.resolveReflective(
                NopTaskFailException.class.getName(), ERR_TEST.getErrorCode(), cause);
        assertNotNull(exp);
        assertEquals(cause, exp.getCause(), "reflective resolution must preserve cause");
    }

    // ==================== 反射 ClassNotFound 回退（#24 无静默吞掉）====================

    /**
     * Exit Criteria: 向后兼容——未注册类型 / ClassNotFoundException（部署缺类）→ 回退 generic NopException。
     * NopAiAgentException 不在 nop-task-dao test classpath → Class.forName 失败 → 返回 null（调用方回退 generic）。
     */
    @Test
    public void create_reflectiveMissingClass_returnsNull() {
        // NopAiAgentException 在 nop-task-ext test classpath 不可用（nop-task-ext 不依赖 nop-ai-agent）
        NopException exp = registry.create("io.nop.ai.agent.engine.NopAiAgentException",
                ERR_TEST.getErrorCode(), null);
        assertNull(exp, "registry must return null for class-not-found (deployment without nop-ai-agent), enabling generic fallback");
    }

    // ==================== 未注册 / null 回退 ====================

    /**
     * 未注册 FQCN → 返回 null（调用方回退 generic NopException）。
     */
    @Test
    public void create_unregisteredFqcn_returnsNull() {
        NopException exp = registry.create("com.example.UnknownException", ERR_TEST.getErrorCode(), null);
        assertNull(exp, "registry must return null for unregistered FQCN");
    }

    /**
     * null / 空 FQCN → 返回 null。
     */
    @Test
    public void create_nullOrEmptyFqcn_returnsNull() {
        assertNull(registry.create(null, ERR_TEST.getErrorCode(), null), "null FQCN must return null");
        assertNull(registry.create("", ERR_TEST.getErrorCode(), null), "empty FQCN must return null");
    }

    /**
     * 缓存验证：ClassNotFound 后再次 create 不重复反射（reflectiveMissing 缓存）。
     */
    @Test
    public void create_missingClassCached_subsequentLookupReturnsNullWithoutReflection() {
        String fqcn = "io.nop.ai.agent.engine.NopAiAgentException";
        assertNull(registry.create(fqcn, ERR_TEST.getErrorCode(), null), "first lookup: class not found");
        // 第二次：应命中缓存直接返回 null（不重复 Class.forName）
        assertNull(registry.create(fqcn, ERR_TEST.getErrorCode(), null), "second lookup: cached as missing");
    }

    /**
     * 非 NopException 子类 → 返回 null（防御性）。
     */
    @Test
    public void resolveReflective_nonNopException_returnsNull() {
        NopException exp = registry.resolveReflective("java.lang.String", ERR_TEST.getErrorCode(), null);
        assertNull(exp, "reflective resolution must reject non-NopException class");
    }

    /**
     * registry.create 返回的 exception 不携带 reserved __exceptionClass param（过滤由 rebuildExceptionFromErrorBean 负责，
     * registry 只负责构造实例。此测试确认 factory 不泄漏 FQCN 到 params）。
     */
    @Test
    public void create_doesNotLeakFqcnParam() {
        NopException exp = registry.create(NopTaskFailException.class.getName(), ERR_TEST.getErrorCode(), null);
        assertNotNull(exp);
        assertFalse(exp.getParams().containsKey(DaoTaskStateStore.PARAM_EXCEPTION_CLASS),
                "registry factory must not leak reserved __exceptionClass param");
    }
}
