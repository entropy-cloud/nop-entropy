package io.nop.xlang.java.compare;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.impl.EvalMethodInvoker;
import io.nop.core.reflect.impl.MethodInvoker;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import io.nop.xlang.compare.BackendExecRequest;
import io.nop.xlang.compare.BackendExecutionEvidence;
import io.nop.xlang.compare.BackendExecutionResult;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.IEvalBackendColumn;
import io.nop.xlang.java.gen.GeneratedEvalBinding;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 对拍矩阵 java 列（I2 落地，I1 harness 的真实后端列）。
 *
 * <p>测试域执行通路（Phase 4 决策）：列以请求中的<b>同一棵 Executable 树实例</b>为翻译源
 * （对拍对象是树，差异只可能来自后端），经转译器产出生成源码，由 {@link JdkJavaCompiler}
 * （nop-javac 内存编译通路）编译加载后执行——与设计裁定一致（nop-javac 不承担生产产物编译；
 * 生产通路 = I11 常规构建编译 + I10 classpath 绑定，本列不实现不宣称）。
 *
 * <p>证据契约：executedArtifact = 生成类实例（身份断言依据，由 harness 经
 * {@link JavaBackendIdentityRule} 依据证据判定，列不自证）；executorArtifact = 生成类入口
 * {@link Method}（EvalMethod 约定）。静态单元适用；动态单元 java 列不适用（无生成类，
 * I1 列适用性机制）。
 */
public final class JavaBackendColumn implements IEvalBackendColumn {

    public static final JavaBackendColumn INSTANCE = new JavaBackendColumn();

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();

    private JavaBackendColumn() {
    }

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
        GeneratedJavaSource source = TRANSLATOR.translate(unit.getSourceLocationPath(), request.getExpr());

        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();
        JavaCompileResult result = compiler.compile(source.getClassName(), source.getCode(), classPaths);
        if (!result.isSuccess())
            throw new IllegalStateException("generated source compile failed: " + result.getErrorMessage()
                    + "\n==== generated code ====\n" + source.getCode());
        Class<?> generatedClass = result.getGeneratedClass(source.getClassName());

        Object generatedInstance;
        Method entry;
        IEvalFunction bound;
        try {
            generatedInstance = generatedClass.getDeclaredConstructor().newInstance();
            entry = GeneratedEvalBinding.findEntryMethod(generatedClass);
            bound = new EvalMethodInvoker(new MethodInvoker(entry));
        } catch (Throwable t) {
            throw new IllegalStateException("java column execution setup failed: " + source.getResourcePath(), t);
        }
        BackendExecutionEvidence evidence = new BackendExecutionEvidence(generatedInstance, entry);
        try {
            Object ret = bound.invoke(generatedInstance, new Object[0], request.getScope());
            return BackendExecutionResult.value(ret, evidence);
        } catch (Throwable t) {
            // 单元执行异常是三层断言的语料（异常层），作为证据返回而非列崩溃
            return BackendExecutionResult.error(t, evidence);
        }
    }
}
