package io.nop.xlang.java.compare;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.reflect.impl.EvalMethodInvoker;
import io.nop.core.reflect.impl.MethodInvoker;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.IEvalStaticBinding;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.java.backend.IEvalStaticBindingBinder;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.GeneratedEvalBinding;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由场景矩阵 java 侧（Phase 1 §6 落点 b；roadmap I9 验收）：场景用例经真实出口
 * （compile→invoke→统一裁决入口）执行，以 I1 harness 断言工具（CompareValues 三层口径）
 * + 裁决环身份断言（java=生成类绑定 artifact）+ 降级观测断言（WARN ListAppender + 指标）
 * 收口。生成类执行体 = 测试域合成（转译器 + nop-javac 内存编译，I2 先例）；生产绑定归 I10。
 */
public class TestJavaBackendRoutingScenarios {

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();

    private static final String STATIC_PATH = "/route/static/java-scenario.expr";

    private static final String TPL_PATH = "/route/static/java-scenario-tpl.xpl";

    private static IConfigReference<Boolean> javaSwitch;

    private static Boolean switchBaseline;

    private ListAppender<ILoggingEvent> appender;

    private Logger observationLogger;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        javaSwitch = CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
        switchBaseline = javaSwitch.get();
    }

    @AfterAll
    public static void resetBackend() {
        AppConfig.getConfigProvider().updateConfigValue(javaSwitch, switchBaseline);
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    @BeforeEach
    public void setUp() {
        observationLogger = (Logger) LoggerFactory.getLogger(EvalBackendObservation.class);
        appender = new ListAppender<>();
        appender.start();
        observationLogger.addAppender(appender);
        EvalBackendRouter.instance().clearRecentDecisions();
        // 缺省场景前置：开关启用 + 后端可用 + 合成清单/绑定就绪（各用例按需覆盖）
        AppConfig.getConfigProvider().updateConfigValue(javaSwitch, true);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        JavaEvalExecutionBackend.instance().setStaticScanList(List.of(STATIC_PATH, TPL_PATH));
        JavaEvalExecutionBackend.instance().setBinder(syntheticBinder());
    }

    @AfterEach
    public void tearDown() {
        observationLogger.detachAppender(appender);
        appender.stop();
        AppConfig.getConfigProvider().updateConfigValue(javaSwitch, true);
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    /**
     * 合成绑定供给方（I2 测试域通路先例）：转译器产源码 → nop-javac 内存编译 →
     * 入口 Method 反射执行（$out 双参形态直接注入 EvalRuntime 的输出缓冲）。
     */
    private static IEvalStaticBindingBinder syntheticBinder() {
        return (resourcePath, tree) -> {
            GeneratedJavaSource source = TRANSLATOR.translate(resourcePath, tree);
            JdkJavaCompiler compiler = new JdkJavaCompiler();
            JavaCompileResult result = compiler.compile(source.getClassName(), source.getCode(),
                    JdkJavaCompiler.getDefaultClassPaths());
            if (!result.isSuccess())
                throw new IllegalStateException("synthetic binding compile failed: " + result.getErrorMessage());
            Class<?> generatedClass = result.getGeneratedClass(source.getClassName());
            try {
                Object instance = generatedClass.getDeclaredConstructor().newInstance();
                Method entry = GeneratedEvalBinding.findEntryMethod(generatedClass);
                boolean usesOut = entry.getParameterCount() == 2;
                return new IEvalStaticBinding() {
                    @Override
                    public Object execute(EvalRuntime rt) {
                        try {
                            if (usesOut)
                                return entry.invoke(instance, rt.getScope(), rt.getOut());
                            IEvalFunction bound = new EvalMethodInvoker(new MethodInvoker(entry));
                            return bound.invoke(instance, new Object[0], rt.getScope());
                        } catch (Throwable t) {
                            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                                    && t.getCause() != null ? t.getCause() : t;
                            if (cause instanceof RuntimeException)
                                throw (RuntimeException) cause;
                            if (cause instanceof Error)
                                throw (Error) cause;
                            throw new java.io.UncheckedIOException(
                                    new java.io.IOException("synthetic binding eval failed", cause));
                        }
                    }

                    @Override
                    public Object getBindingArtifact() {
                        return entry;
                    }
                };
            } catch (Throwable t) {
                throw new IllegalStateException("synthetic binding setup failed: " + resourcePath, t);
            }
        };
    }

    // ---- 场景一：静态→java ----

    @Test
    public void testStaticRoutesToGeneratedClass() {
        SourceLocation loc = SourceLocation.fromPath(STATIC_PATH);
        // 值层 + scope 读层：输入变量经生成类执行体与解释器一致
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileSimpleExpr(loc, "x * 2 + 1");
        IExecutableExpression tree = action.getExpr();
        assertEquals(STATIC_PATH, tree.getLocation().getPath());

        IEvalScope scope = EvalExprProvider.newEvalScope(Map.of("x", 10));
        Object routedResult = action.invoke(scope);

        // 身份断言：裁决落点 STATIC，执行体 = 生成类入口 Method（确定性派生类名）
        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.STATIC, decision.getKind());
        assertEquals("java", decision.getBackendId());
        assertFalse(decision.isDegraded());
        Method entry = (Method) decision.getArtifact();
        assertNotNull(entry);
        assertEquals(EvalMethodConvention.GENERATED_PACKAGE + "."
                + EvalMethodConvention.generatedClassName(STATIC_PATH), entry.getDeclaringClass().getName());

        // 三层对拍：同一出口关路由（清单清空）→ 解释器执行，返回值 typedEquals + scope 变量一致
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        IEvalScope baselineScope = EvalExprProvider.newEvalScope(Map.of("x", 10));
        Object baselineResult = action.invoke(baselineScope);

        assertTrue(CompareValues.typedEquals(baselineResult, routedResult), "baseline=" + baselineResult
                + " routed=" + routedResult);
        assertEquals(21, routedResult);
        // 副作用层：输入变量两侧均不被改写
        assertEquals(10, scope.getValue("x"));
        assertEquals(10, baselineScope.getValue("x"));
    }

    @Test
    public void testStaticTemplateRoutesToGeneratedClassWithOutput() throws Exception {
        // 输出语义单元（$out 双参入口）：真实出口 compileTag(text) → invoke（generateToWriter）
        // → 裁决 → 生成类执行
        SourceLocation loc = SourceLocation.fromPath(TPL_PATH);
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileTag(io.nop.core.lang.xml.parse.XNodeParser.instance()
                                .parseFromText(loc, "<c:for var='v' items='${[6]}'>a${v * 7}</c:for>"),
                        io.nop.xlang.ast.XLangOutputMode.text);

        StringWriter routedWriter = new StringWriter();
        action.generateToWriter(routedWriter, EvalExprProvider.newEvalScope());
        assertEquals("a42", routedWriter.toString());

        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.STATIC, decision.getKind());
        Method entry = (Method) decision.getArtifact();
        assertEquals(2, entry.getParameterCount(), "output-semantics unit uses $out two-param entry");

        // 输出层对拍：关路由后解释器渲染结果一致
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        StringWriter baselineWriter = new StringWriter();
        action.generateToWriter(baselineWriter, EvalExprProvider.newEvalScope());
        assertEquals(baselineWriter.toString(), routedWriter.toString());
    }

    // ---- 场景二：java 降级→解释器（开关关形态） ----

    @Test
    public void testJavaDegradedByConfigSwitch() {
        SourceLocation loc = SourceLocation.fromPath(STATIC_PATH);
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(loc, "5 + 5");
        IExecutableExpression tree = action.getExpr();

        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_CONFIG_DISABLED);
        AppConfig.getConfigProvider().updateConfigValue(javaSwitch, false);
        try {
            Object result = action.invoke(EvalExprProvider.newEvalScope());
            assertEquals(10, result);

            // 身份断言：解释器执行（裁决环 artifact = 原树实例）
            EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
            assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertSame(tree, decision.getArtifact());

            // 观测断言：WARN（消息键 + backend/reason）+ 指标计数
            assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                    EvalBackendObservation.REASON_CONFIG_DISABLED) - before, 1e-9);
            assertEquals(1, appender.list.size());
            assertEquals(Level.WARN, appender.list.get(0).getLevel());
            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains(EvalBackendObservation.LOG_MESSAGE_KEY), message);
            assertTrue(message.contains("backend=java"), message);
            assertTrue(message.contains("reason=" + EvalBackendObservation.REASON_CONFIG_DISABLED), message);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(javaSwitch, true);
        }
    }

    // ---- 场景三：java 降级→解释器（不可用条目形态） ----

    @Test
    public void testJavaDegradedByUnavailableEntry() {
        SourceLocation loc = SourceLocation.fromPath(STATIC_PATH);
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(loc, "6 + 6");
        IExecutableExpression tree = action.getExpr();

        double before = EvalBackendObservation.degradationCount("java", EvalBackendObservation.REASON_UNAVAILABLE);
        JavaEvalExecutionBackend.instance().markUnavailable("build-pipeline-missed: injected");
        try {
            Object result = action.invoke(EvalExprProvider.newEvalScope());
            assertEquals(12, result);

            EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
            assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertTrue(decision.getReason().contains("build-pipeline-missed"));
            assertSame(tree, decision.getArtifact());

            assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                    EvalBackendObservation.REASON_UNAVAILABLE) - before, 1e-9);
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("reason=" + EvalBackendObservation.REASON_UNAVAILABLE)),
                    () -> String.valueOf(appender.list));
        } finally {
            JavaEvalExecutionBackend.instance().clearUnavailable();
        }
    }
}
