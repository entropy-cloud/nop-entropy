package io.nop.xlang.compare;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * 三后端对拍验证 harness（测试域显式注册表，不做 classpath 扫描）。
 *
 * <p>执行语义：同一单元的多列执行使用同一棵 Executable 树实例；每列独立新建、按单元声明
 * 等价初始化的求值现场（IEvalScope + 录制输出缓冲）；副作用快照逐列在各自现场上获取。
 *
 * <p>断言口径（设计 execution 01 §五，缺一不可）：
 * 返回值 equals 含类型；副作用 = 本地变量集封闭化比对 + 输出缓冲完整 API 调用序列比对；
 * 异常 = 错误码一致 + SourceLocation 回映射 + 不允许一侧抛异常一侧正常返回；
 * 后端身份断言由 harness 依据执行证据判定（列不得自证），无规则可断言的列显式 FAIL。
 *
 * <p>列适用性与缺席记录：按单元静态/动态类别 × 已注册列执行；缺席的期望列显式记 skipped（含原因），
 * 不计入通过。列不足 2 列时，判定基准 = 列结果 vs 单元声明的预期；≥2 列时叠加列间逐项比对。
 */
public final class ExecCompareHarness {
    private final Map<String, IEvalBackendColumn> columns = new LinkedHashMap<>();
    private final Map<String, IBackendIdentityRule> identityRules = new HashMap<>();
    private final Map<String, IEvalBackendColumn> columnsView = Collections.unmodifiableMap(columns);

    private ICompareUnitCompiler compiler;

    public static ExecCompareHarness withInterpreterBaseline() {
        ExecCompareHarness harness = new ExecCompareHarness();
        harness.registerColumn(InterpreterBackendColumn.INSTANCE);
        harness.registerIdentityRule(InterpreterIdentityRule.INSTANCE);
        return harness;
    }

    public void setCompiler(ICompareUnitCompiler compiler) {
        this.compiler = compiler;
    }

    public void registerColumn(IEvalBackendColumn column) {
        Objects.requireNonNull(column, "column");
        IEvalBackendColumn old = columns.putIfAbsent(column.getBackendId(), column);
        if (old != null)
            throw new IllegalStateException("backend column already registered: " + column.getBackendId());
    }

    public IEvalBackendColumn getColumn(String backendId) {
        return columns.get(backendId);
    }

    public Map<String, IEvalBackendColumn> getColumns() {
        return columnsView;
    }

    public void registerIdentityRule(IBackendIdentityRule rule) {
        Objects.requireNonNull(rule, "rule");
        IBackendIdentityRule old = identityRules.putIfAbsent(rule.getBackendId(), rule);
        if (old != null)
            throw new IllegalStateException("identity rule already registered: " + rule.getBackendId());
    }

    public void replaceIdentityRule(IBackendIdentityRule rule) {
        Objects.requireNonNull(rule, "rule");
        identityRules.put(rule.getBackendId(), rule);
    }

    public IBackendIdentityRule getIdentityRule(String backendId) {
        return identityRules.get(backendId);
    }

    /**
     * 测试级强制路由 API：指定后端执行单元并返回执行结果（含身份证据）。身份断言由 harness
     * 依据证据完成；返回值/副作用/异常的判定由调用方做出（供 I2/I5/I9 测试复用）。
     */
    public ColumnOutcome routeAndExecute(CompareUnit unit, String backendId) {
        Objects.requireNonNull(unit, "unit");
        IEvalBackendColumn column = columns.get(backendId);
        if (column == null)
            throw new NoSuchElementException("backend column not registered: " + backendId);
        IExecutableExpression tree = compileTree(unit);
        ColumnExecution execution = executeColumn(unit, column, tree);
        return execution.buildOutcome();
    }

    public CompareUnitReport runUnit(CompareUnit unit) {
        Objects.requireNonNull(unit, "unit");
        return runUnit(unit, compileTree(unit));
    }

    /**
     * 以调用方提供的树实例执行矩阵（对拍对象是树：同一实例分列执行）。
     */
    public CompareUnitReport runUnit(CompareUnit unit, IExecutableExpression tree) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(tree, "tree");
        ExpectedOutcome expectation = unit.getExpectation();
        if (expectation == null || (!expectation.hasReturnValue() && !expectation.isExpectException()))
            throw new IllegalArgumentException(
                    "unit expectation must declare return value or exception: " + unit.getName());

        boolean staticUnit = unit.getKind() == CompareUnitKind.STATIC;
        List<ColumnExecution> executions = new ArrayList<>();
        for (IEvalBackendColumn column : columns.values()) {
            boolean capable = staticUnit ? column.isSupportsStaticUnits() : column.isSupportsDynamicUnits();
            if (capable)
                executions.add(executeColumn(unit, column, tree));
        }

        if (executions.size() >= 2)
            crossCompareColumns(executions);

        List<ColumnSkipRecord> skips = new ArrayList<>();
        Set<String> executedIds = new LinkedHashSet<>();
        for (ColumnExecution execution : executions)
            executedIds.add(execution.column.getBackendId());
        for (String expectedId : unit.getKind().getExpectedBackendIds()) {
            if (executedIds.contains(expectedId))
                continue;
            IEvalBackendColumn registered = columns.get(expectedId);
            if (registered == null) {
                skips.add(new ColumnSkipRecord(expectedId, ColumnSkipRecord.REASON_NOT_REGISTERED));
            } else {
                boolean capable = staticUnit ? registered.isSupportsStaticUnits() : registered.isSupportsDynamicUnits();
                if (!capable)
                    skips.add(new ColumnSkipRecord(expectedId, ColumnSkipRecord.REASON_CAPABILITY_NOT_DECLARED));
            }
        }

        List<ColumnOutcome> outcomes = new ArrayList<>(executions.size());
        for (ColumnExecution execution : executions)
            outcomes.add(execution.buildOutcome());
        return new CompareUnitReport(unit, tree, outcomes, skips);
    }

    private IExecutableExpression compileTree(CompareUnit unit) {
        if (compiler == null)
            throw new IllegalStateException("no unit compiler registered on harness");
        IExecutableExpression tree = compiler.compile(unit);
        if (tree == null)
            throw new IllegalStateException("unit compiler returned null tree: " + unit.getName());
        return tree;
    }

    private ColumnExecution executeColumn(CompareUnit unit, IEvalBackendColumn column, IExecutableExpression tree) {
        IEvalScope scope = new EvalScopeImpl(new LinkedHashMap<>(unit.getInputVars()));
        RecordingEvalOutput out = new RecordingEvalOutput();
        BackendExecRequest request = new BackendExecRequest(unit, tree, scope, out);

        BackendExecutionResult result = null;
        List<String> failures = new ArrayList<>();
        try {
            result = column.execute(request);
            if (result == null)
                failures.add("column-crashed: column returned null result");
            else if (result.getEvidence() == null)
                failures.add("identity-unverifiable: column returned no execution evidence");
        } catch (Throwable t) {
            failures.add("column-crashed: " + t);
        }

        if (result != null && result.getEvidence() != null) {
            IBackendIdentityRule rule = identityRules.get(column.getBackendId());
            if (rule == null) {
                failures.add("identity-unverifiable: no identity rule registered for backend=" + column.getBackendId());
            } else {
                try {
                    rule.verifyIdentity(result.getEvidence(), request);
                } catch (AssertionError e) {
                    failures.add("identity-mismatch: " + e);
                } catch (Throwable t) {
                    failures.add("identity-rule-crashed: " + t);
                }
            }
        }

        assertAgainstExpectation(unit, result, scope, out, failures);

        SideEffectSnapshot snapshot = snapshot(scope, out);
        return new ColumnExecution(column, result, snapshot, failures);
    }

    /**
     * 三层断言（vs 单元声明的预期）。副作用未显式声明时按"输入变量集不变 + 无输出"断言，
     * 保证副作用层始终非 vacuous。
     */
    private void assertAgainstExpectation(CompareUnit unit, BackendExecutionResult result,
                                          IEvalScope scope, RecordingEvalOutput out, List<String> failures) {
        ExpectedOutcome expectation = unit.getExpectation();

        if (expectation.isExpectException()) {
            assertExpectedException(expectation, result, failures);
        } else if (expectation.hasReturnValue()) {
            Throwable thrown = result == null ? null : result.getThrown();
            if (thrown != null) {
                failures.add("normal-return-expected-but-exception-thrown: " + thrown);
            } else {
                Object actual = result == null ? null : result.getReturnValue();
                if (!CompareValues.typedEquals(expectation.getReturnValue(), actual))
                    failures.add("return-value-mismatch: "
                            + CompareValues.describeTypedMismatch(expectation.getReturnValue(), actual));
            }
        }

        Map<String, Object> expectedVars = expectation.hasExpectedScopeVars()
                ? expectation.getExpectedScopeVars() : unit.getInputVars();
        assertScopeVars(expectedVars, scope, failures);

        List<RecordedOutputCall> expectedCalls = expectation.hasExpectedOutputCalls()
                ? expectation.getExpectedOutputCalls() : List.of();
        if (!out.getCalls().equals(expectedCalls))
            failures.add("output-calls-mismatch: expected=" + expectedCalls + ", was=" + out.getCalls());
    }

    private void assertExpectedException(ExpectedOutcome expectation, BackendExecutionResult result,
                                         List<String> failures) {
        Throwable thrown = result == null ? null : result.getThrown();
        if (thrown == null) {
            failures.add("exception-expected-but-normal-return: errorCode=" + expectation.getErrorCode());
            return;
        }
        if (!(thrown instanceof NopException)) {
            failures.add("exception-not-nop-exception: " + thrown.getClass().getName() + ": " + thrown.getMessage());
            return;
        }
        NopException ne = (NopException) thrown;
        if (!Objects.equals(expectation.getErrorCode(), ne.getErrorCode()))
            failures.add("exception-error-code-mismatch: expected=" + expectation.getErrorCode()
                    + ", was=" + ne.getErrorCode());
        assertErrorLocation(expectation, ne, failures);
    }

    private void assertErrorLocation(ExpectedOutcome expectation, NopException ne, List<String> failures) {
        SourceLocation expected = expectation.getExpectedErrorLocation();
        if (expected == null)
            return;
        SourceLocation actual = ne.getErrorLocation();
        if (actual == null) {
            failures.add("exception-location-missing: expected=" + expected);
            return;
        }
        if (!Objects.equals(expected.getPath(), actual.getPath())) {
            failures.add("exception-location-path-mismatch: expected=" + expected.getPath()
                    + ", was=" + actual.getPath());
            return;
        }
        if (expected.getLine() > 0 && expected.getLine() != actual.getLine())
            failures.add("exception-location-line-mismatch: expected=" + expected + ", was=" + actual);
    }

    private void assertScopeVars(Map<String, Object> expectedVars, IEvalScope scope, List<String> failures) {
        Set<String> expectedKeys = new LinkedHashSet<>(expectedVars.keySet());
        Set<String> actualKeys = new LinkedHashSet<>(scope.keySet());
        if (!expectedKeys.equals(actualKeys)) {
            failures.add("scope-vars-keyset-mismatch: expected=" + expectedKeys + ", was=" + actualKeys);
            return;
        }
        for (String key : expectedKeys) {
            Object expected = expectedVars.get(key);
            Object actual = scope.getLocalValue(key);
            if (!CompareValues.typedEquals(expected, actual))
                failures.add("scope-var-mismatch[" + key + "]: "
                        + CompareValues.describeTypedMismatch(expected, actual));
        }
    }

    /**
     * 列间逐项比对（≥2 列时叠加）。解释器列为参照列（未注册时取首列）：any-divergence
     * 使单元判 FAIL；分歧失败归因到非参照列（参照列自身的判定仍由身份断言与声明预期驱动）。
     */
    private void crossCompareColumns(List<ColumnExecution> executions) {
        ColumnExecution reference = null;
        for (ColumnExecution execution : executions) {
            if (CompareBackendIds.INTERPRETER.equals(execution.column.getBackendId())) {
                reference = execution;
                break;
            }
        }
        if (reference == null)
            reference = executions.get(0);
        for (ColumnExecution execution : executions) {
            if (execution == reference)
                continue;
            crossComparePair(reference, execution);
        }
    }

    private void crossComparePair(ColumnExecution a, ColumnExecution b) {
        BackendExecutionResult ra = a.result;
        BackendExecutionResult rb = b.result;
        if (ra == null || rb == null)
            return;

        Throwable ta = ra.getThrown();
        Throwable tb = rb.getThrown();
        if (ta == null && tb == null) {
            if (!CompareValues.typedEquals(ra.getReturnValue(), rb.getReturnValue())) {
                addPairFailure(a, b, "cross-column-return-divergence: "
                        + CompareValues.describeTypedMismatch(ra.getReturnValue(), rb.getReturnValue()));
            }
        } else if (ta != null && tb != null) {
            crossCompareException(a, b, ta, tb);
        } else {
            addPairFailure(a, b, "cross-column-exception-divergence: one side threw ("
                    + (ta != null ? ta : tb) + ") while the other returned normally");
        }

        crossCompareSnapshot(a, b);
    }

    private void crossCompareException(ColumnExecution a, ColumnExecution b, Throwable ta, Throwable tb) {
        String codeA = ta instanceof NopException ? ((NopException) ta).getErrorCode() : ta.getClass().getName();
        String codeB = tb instanceof NopException ? ((NopException) tb).getErrorCode() : tb.getClass().getName();
        if (!Objects.equals(codeA, codeB)) {
            addPairFailure(a, b, "cross-column-exception-error-code-divergence: " + codeA + " vs " + codeB);
            return;
        }
        SourceLocation la = ta instanceof NopException ? ((NopException) ta).getErrorLocation() : null;
        SourceLocation lb = tb instanceof NopException ? ((NopException) tb).getErrorLocation() : null;
        boolean locEquals = la == null ? lb == null
                : lb != null && Objects.equals(la.getPath(), lb.getPath())
                        && la.getLine() == lb.getLine() && la.getCol() == lb.getCol();
        if (!locEquals)
            addPairFailure(a, b, "cross-column-exception-location-divergence: " + la + " vs " + lb);
    }

    private void crossCompareSnapshot(ColumnExecution a, ColumnExecution b) {
        Map<String, Object> varsA = a.snapshot.getLocalVars();
        Map<String, Object> varsB = b.snapshot.getLocalVars();
        if (!varsA.keySet().equals(varsB.keySet())) {
            addPairFailure(a, b, "cross-column-scope-vars-keyset-divergence: " + varsA.keySet() + " vs " + varsB.keySet());
        } else {
            for (String key : varsA.keySet()) {
                if (!CompareValues.typedEquals(varsA.get(key), varsB.get(key))) {
                    addPairFailure(a, b, "cross-column-scope-var-divergence[" + key + "]: "
                            + CompareValues.describeTypedMismatch(varsA.get(key), varsB.get(key)));
                }
            }
        }
        if (!a.snapshot.getOutputCalls().equals(b.snapshot.getOutputCalls()))
            addPairFailure(a, b, "cross-column-output-calls-divergence: " + a.snapshot.getOutputCalls()
                    + " vs " + b.snapshot.getOutputCalls());
    }

    private void addPairFailure(ColumnExecution reference, ColumnExecution diverged, String message) {
        diverged.failures.add(message + " [diverged from reference column " + reference.column.getBackendId() + "]");
    }

    private SideEffectSnapshot snapshot(IEvalScope scope, RecordingEvalOutput out) {
        Map<String, Object> vars = new LinkedHashMap<>();
        for (String key : scope.keySet())
            vars.put(key, scope.getLocalValue(key));
        return new SideEffectSnapshot(vars, out.getCalls());
    }

    private static final class ColumnExecution {
        final IEvalBackendColumn column;
        final BackendExecutionResult result;
        final SideEffectSnapshot snapshot;
        final List<String> failures;

        ColumnExecution(IEvalBackendColumn column, BackendExecutionResult result,
                        SideEffectSnapshot snapshot, List<String> failures) {
            this.column = column;
            this.result = result;
            this.snapshot = snapshot;
            this.failures = failures;
        }

        ColumnOutcome buildOutcome() {
            return new ColumnOutcome(column.getBackendId(),
                    failures.isEmpty() ? ColumnOutcome.Status.PASS : ColumnOutcome.Status.FAIL,
                    failures, result, snapshot);
        }
    }
}
