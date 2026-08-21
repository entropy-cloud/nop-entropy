package io.nop.xlang.e2e.suite;

import com.oracle.truffle.api.RootCallTarget;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.compare.BackendExecRequest;
import io.nop.xlang.compare.BackendExecutionEvidence;
import io.nop.xlang.compare.BackendExecutionResult;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.IBackendIdentityRule;
import io.nop.xlang.compare.IEvalBackendColumn;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.nodes.XLangRootNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 套件三列 + 身份规则（I12 Phase 1 D1：载体内再实现——harness public API + 两后端 main API，
 * 生产绑定形态免 nop-javac；两内核模块零 test-jar 发布）。
 *
 * <ul>
 * <li><b>java 列 = 生产绑定路径形态</b>（非测试直驱/非内存编译）：模型加载（
 * {@code XLang.parseXpl} 绑定 hook，classpath 双清单供给）→ 已绑定执行体 →
 * {@code XLang.execute} choke point 直通绑定体。</li>
 * <li><b>truffle 列</b>：翻译 AST 经 CallTarget（{@link XLangTruffleEval} 宿主侧 facade，
 * 与 I5 对拍列同一 main API 驱动形态）；静态 sourceKey = 物化 VFS 路径，动态 = 源内容哈希键。</li>
 * <li><b>身份规则</b>：java = 确定性派生生成类实例 + static 入口 Method（EvalMethod 约定）；
 * truffle = 翻译 XLangRootNode（sourceTree 同一）+ RootCallTarget；解释器列复用 harness
 * 基线列与身份规则（非 bound 执行体——干净解析取树经全局执行器）。</li>
 * </ul>
 */
final class SuiteColumns {

    private SuiteColumns() {
    }

    /** java 列：生产绑定路径（双清单装载 + Class.forName 产物类 + choke point 直通） */
    static final class ProductionJavaColumn implements IEvalBackendColumn {

        @Override
        public String getBackendId() {
            return CompareBackendIds.JAVA;
        }

        @Override
        public boolean isSupportsStaticUnits() {
            return true;
        }

        @Override
        public boolean isSupportsDynamicUnits() {
            return false;
        }

        @Override
        public BackendExecutionResult execute(BackendExecRequest request) {
            CompareUnit unit = request.getUnit();
            // 生产绑定全链：物化资源经绑定 hook 装载（清单成员 → Class.forName 生成类绑定）
            XplModel model = XLang.parseXpl(
                    VirtualFileSystem.instance().getResource(unit.getSourceLocationPath()),
                    XLangOutputMode.html);
            if (model == null || !(model.getExpr() instanceof EvalStaticBoundExecutable))
                throw new IllegalStateException("production column requires a bound model for manifest member "
                        + unit.getSourceLocationPath()
                        + " (degradation would be a unit-level FAIL, not a skip)");
            Method entry = (Method) ((EvalStaticBoundExecutable) model.getExpr()).getBinding().getBindingArtifact();
            Object generatedInstance;
            try {
                generatedInstance = entry.getDeclaringClass().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("generated class instantiation failed: "
                        + unit.getSourceLocationPath(), e);
            }
            BackendExecutionEvidence evidence = new BackendExecutionEvidence(generatedInstance, entry);
            try {
                // choke point 直通绑定体：scope + 录制输出缓冲经 EvalRuntime 传入
                Object ret = XLang.execute(model.getExpr(),
                        new io.nop.core.lang.eval.EvalRuntime(request.getScope(), request.getOut()));
                return BackendExecutionResult.value(ret, evidence);
            } catch (Throwable t) {
                return BackendExecutionResult.error(t, evidence);
            }
        }
    }

    /** java 列身份规则：确定性派生生成类实例 + static 入口（EvalMethod 约定） */
    static final class JavaIdentityRule implements IBackendIdentityRule {

        @Override
        public String getBackendId() {
            return CompareBackendIds.JAVA;
        }

        @Override
        public void verifyIdentity(BackendExecutionEvidence evidence, BackendExecRequest request) {
            Object executed = evidence.getExecutedArtifact();
            if (executed == null)
                throw new AssertionError("java column must report the generated class instance as executed artifact");
            String expected = EvalMethodConvention.GENERATED_PACKAGE + '.'
                    + EvalMethodConvention.generatedClassName(request.getUnit().getSourceLocationPath());
            String actual = executed.getClass().getName();
            if (!expected.equals(actual))
                throw new AssertionError("java column executed artifact must be an instance of the "
                        + "deterministically generated class for this unit: expected=" + expected
                        + ", was=" + actual);

            Object executor = evidence.getExecutorArtifact();
            if (!(executor instanceof Method))
                throw new AssertionError("java column executor artifact must be the generated entry Method, was: "
                        + executor);
            Method entry = (Method) executor;
            if (!expected.equals(entry.getDeclaringClass().getName()))
                throw new AssertionError("java column entry method must be declared on the generated class: expected="
                        + expected + ", was=" + entry.getDeclaringClass().getName());
            if (!Modifier.isStatic(entry.getModifiers()))
                throw new AssertionError("java column entry method must be static (EvalMethod convention): " + entry);
            if (entry.getParameterCount() < 1 || entry.getParameterTypes()[0] != IEvalScope.class)
                throw new AssertionError("java column entry method must take IEvalScope as first parameter "
                        + "(EvalMethod convention): " + entry);
        }
    }

    /** truffle 列：翻译 AST 经 CallTarget（XLangTruffleEval main API 驱动） */
    static final class SuiteTruffleColumn implements IEvalBackendColumn, AutoCloseable {

        private final XLangTruffleEval eval = new XLangTruffleEval();

        @Override
        public String getBackendId() {
            return CompareBackendIds.TRUFFLE;
        }

        @Override
        public boolean isSupportsStaticUnits() {
            return true;
        }

        @Override
        public boolean isSupportsDynamicUnits() {
            return true;
        }

        @Override
        public BackendExecutionResult execute(BackendExecRequest request) {
            CompareUnit unit = request.getUnit();
            String sourceKey = unit.getKind() == CompareUnitKind.STATIC
                    ? unit.getSourceLocationPath()
                    : XLangTruffleEval.dynamicSourceKey(unit.getSource());
            XLangTruffleEval.TranslatedEval result = eval.eval(sourceKey, request.getExpr(),
                    request.getScope(), request.getOut());
            BackendExecutionEvidence evidence = new BackendExecutionEvidence(
                    result.getUnit().getRootNode(), result.getUnit().getCallTarget());
            if (result.getThrown() != null)
                return BackendExecutionResult.error(result.getThrown(), evidence);
            return BackendExecutionResult.value(result.getReturnValue(), evidence);
        }

        @Override
        public void close() {
            eval.close();
        }
    }

    /** truffle 列身份规则：翻译 XLangRootNode（sourceTree 同一）+ RootCallTarget */
    static final class TruffleIdentityRule implements IBackendIdentityRule {

        @Override
        public String getBackendId() {
            return CompareBackendIds.TRUFFLE;
        }

        @Override
        public void verifyIdentity(BackendExecutionEvidence evidence, BackendExecRequest request) {
            Object executed = evidence.getExecutedArtifact();
            if (!(executed instanceof XLangRootNode))
                throw new AssertionError("truffle column executed artifact must be the translated XLangRootNode, "
                        + "was: " + executed);
            XLangRootNode root = (XLangRootNode) executed;
            if (root.getSourceTree() != request.getExpr())
                throw new AssertionError("translated root must be derived from the requested executable tree "
                        + "instance: sourceTree=" + root.getSourceTree() + ", request=" + request.getExpr());

            Object executor = evidence.getExecutorArtifact();
            if (!(executor instanceof RootCallTarget))
                throw new AssertionError("truffle column executor artifact must be the translated RootCallTarget, "
                        + "was: " + executor);
            RootCallTarget callTarget = (RootCallTarget) executor;
            if (callTarget.getRootNode() != root)
                throw new AssertionError("truffle column call target must drive the translated root node: "
                        + "target root=" + callTarget.getRootNode() + ", evidence root=" + root);
        }
    }
}
