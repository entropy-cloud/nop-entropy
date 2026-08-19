package io.nop.xlang.java.compare;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.compare.BackendExecRequest;
import io.nop.xlang.compare.BackendExecutionEvidence;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.IBackendIdentityRule;
import io.nop.xlang.java.gen.EvalMethodConvention;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * java 列强身份规则（替换 I1 占位规则 {@code NonInterpreterArtifactIdentityRule}）：
 * executedArtifact 必须是本单元确定性派生生成类的实例（非解释器树、非伪装）；
 * executorArtifact 必须是该生成类上符合 EvalMethod 约定（static + 首参 IEvalScope）的入口方法。
 */
public final class JavaBackendIdentityRule implements IBackendIdentityRule {

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
                    + "deterministically generated class for this unit: expected=" + expected + ", was=" + actual);

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
