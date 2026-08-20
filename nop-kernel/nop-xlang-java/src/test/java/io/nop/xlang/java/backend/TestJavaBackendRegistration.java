package io.nop.xlang.java.backend;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.backend.IEvalStaticBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * java 后端注册路径测试（Phase 2）：模块初始化显式注册（ICoreInitializer + services）、
 * 能力声明、缺省惰性空态（无清单/无 binder = 无静态成员，行为与现状一致）、不可用条目语义。
 */
public class TestJavaBackendRegistration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterEach
    public void reset() {
        JavaEvalExecutionBackend backend = JavaEvalExecutionBackend.instance();
        backend.setStaticScanList(Collections.emptySet());
        backend.setBinder(null);
        backend.clearUnavailable();
    }

    @Test
    public void testInitializerRegistersBackend() {
        // CoreInitialization 经 META-INF/services 显式注册
        IEvalStaticBackend registered = EvalBackendRegistry.instance().findStaticBackend();
        assertSame(JavaEvalExecutionBackend.instance(), registered);
        assertEquals("java", registered.getBackendId());
        assertTrue(registered.getCapabilities()
                .contains(io.nop.xlang.backend.EvalBackendCapability.STATIC_GENERATED));
        assertTrue(registered.isAvailable());
        assertNull(registered.getUnavailableReason());
        assertTrue(EvalBackendRegistry.instance().getUnavailableBackends().isEmpty());
    }

    @Test
    public void testDefaultInertState() {
        // 缺省：清单空 + binder 缺失 → 无静态成员；绑定查找缺失但不崩溃
        JavaEvalExecutionBackend backend = JavaEvalExecutionBackend.instance();
        assertFalse(backend.isStaticCandidate("/any/resource.xpl"));
        assertNull(backend.findStaticBinding("/any/resource.xpl", null));

        // 静态裁决（清单外）→ 动态路径（无动态后端注册 → 静默解释器）
        EvalBackendDecision decision = io.nop.xlang.backend.EvalBackendRouter.instance()
                .decide("/any/resource.xpl", null);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertFalse(decision.isDegraded());
    }

    @Test
    public void testScanListMembershipAndBindingLookup() {
        JavaEvalExecutionBackend backend = JavaEvalExecutionBackend.instance();
        backend.setStaticScanList(Set.of("/static/member.xpl"));
        assertTrue(backend.isStaticCandidate("/static/member.xpl"));
        assertFalse(backend.isStaticCandidate("/static/other.xpl"));
        assertEquals(Collections.singleton("/static/member.xpl"), backend.getStaticScanList());

        // binder 缺失 → 绑定缺失（清单内应有而缺失 → 降级观测，Phase 3 场景断言）
        assertNull(backend.findStaticBinding("/static/member.xpl", null));

        IExecutableExpression tree = io.nop.xlang.exec.LiteralExecutable.build(
                SourceLocation.fromPath("/static/member.xpl"), 1);
        backend.setBinder((path, t) -> new io.nop.xlang.backend.IEvalStaticBinding() {
            @Override
            public Object execute(EvalRuntime rt) {
                return "bound";
            }

            @Override
            public Object getBindingArtifact() {
                return "synthetic-artifact";
            }
        });
        EvalBackendDecision decision = io.nop.xlang.backend.EvalBackendRouter.instance()
                .decide("/static/member.xpl", tree);
        assertEquals(EvalBackendDecision.RouteKind.STATIC, decision.getKind());
        assertEquals("bound", decision.getStaticBinding().execute(
                new EvalRuntime(EvalExprProvider.newEvalScope())));
    }

    @Test
    public void testMarkUnavailableSemantics() {
        JavaEvalExecutionBackend backend = JavaEvalExecutionBackend.instance();
        backend.markUnavailable("build-pipeline-missed: injected");
        assertFalse(backend.isAvailable());
        assertEquals("build-pipeline-missed: injected", backend.getUnavailableReason());
        assertEquals(Collections.singletonMap("java", "build-pipeline-missed: injected"),
                EvalBackendRegistry.instance().getUnavailableBackends());
    }
}
