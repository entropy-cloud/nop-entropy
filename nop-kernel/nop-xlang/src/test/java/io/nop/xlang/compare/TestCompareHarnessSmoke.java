package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.SeqExecutable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestCompareHarnessSmoke {

    private static IExecutableExpression smokeTree() {
        SourceLocation loc = SourceLocation.fromPath("smoke:/compare/smoke-tree");
        IExecutableExpression plus = new PlusExecutable(loc,
                LiteralExecutable.build(loc, 40), LiteralExecutable.build(loc, 2));
        ScopeAssignExecutable assign = new ScopeAssignExecutable(loc, "result", plus);
        return SeqExecutable.valueOf(loc, new IExecutableExpression[]{assign, LiteralExecutable.build(loc, "done")});
    }

    private static CompareUnit smokeUnit() {
        Map<String, Object> expectedVars = new LinkedHashMap<>();
        expectedVars.put("result", 42);
        return new CompareUnit("smoke", CompareUnitKind.STATIC, "smoke-tree", "smoke:/compare/smoke-tree",
                Map.of(), ExpectedOutcome.returnValue("done").scopeVars(expectedVars), "smoke");
    }

    @Test
    public void testThreeLayerAssertionWithInterpreterColumn() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        IExecutableExpression tree = smokeTree();
        CompareUnitReport report = harness.runUnit(smokeUnit(), tree);

        assertTrue(report.isPassed(), report::toString);
        assertEquals(1, report.getColumnOutcomes().size());
        ColumnOutcome outcome = report.getColumnOutcomes().get(0);
        assertEquals(CompareBackendIds.INTERPRETER, outcome.getBackendId());
        assertTrue(outcome.isPassed(), () -> String.join(";", outcome.getFailures()));

        assertNotNull(outcome.getExecution());
        assertSame(tree, outcome.getExecution().getEvidence().getExecutedArtifact());
        assertEquals(42, outcome.getSnapshot().getLocalVars().get("result"));
    }

    @Test
    public void testColumnAbsenceRecordedAsSkipped() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        CompareUnitReport report = harness.runUnit(smokeUnit(), smokeTree());

        assertEquals(2, report.getSkipRecords().size());
        for (ColumnSkipRecord skip : report.getSkipRecords()) {
            assertTrue(skip.getBackendId().equals(CompareBackendIds.JAVA)
                    || skip.getBackendId().equals(CompareBackendIds.TRUFFLE), skip::toString);
            assertEquals(ColumnSkipRecord.REASON_NOT_REGISTERED, skip.getReason());
        }
        assertTrue(report.isPassed(), report::toString);
    }

    @Test
    public void testIdentityUnverifiableFailsExplicitly() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.registerColumn(new DelegatingColumn("mystery"));
        CompareUnitReport report = harness.runUnit(smokeUnit(), smokeTree());

        assertFalse(report.isPassed(), report::toString);
        ColumnOutcome mystery = findOutcome(report, "mystery");
        assertFalse(mystery.isPassed());
        assertTrue(mystery.getFailures().stream().anyMatch(f -> f.contains("identity-unverifiable")),
                () -> String.join(";", mystery.getFailures()));
        assertTrue(report.getColumnOutcomes().stream()
                .filter(o -> o.getBackendId().equals(CompareBackendIds.INTERPRETER))
                .allMatch(ColumnOutcome::isPassed));
    }

    @Test
    public void testForcedRoutingReturnsEvidence() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        IExecutableExpression tree = smokeTree();
        harness.setCompiler(unit -> tree);

        ColumnOutcome outcome = harness.routeAndExecute(smokeUnit(), CompareBackendIds.INTERPRETER);
        assertTrue(outcome.isPassed(), () -> String.join(";", outcome.getFailures()));
        assertSame(tree, outcome.getExecution().getEvidence().getExecutedArtifact());
        assertSame(EvalExprProvider.getGlobalExecutor(), outcome.getExecution().getEvidence().getExecutorArtifact());

        assertThrows(Exception.class,
                () -> harness.routeAndExecute(smokeUnit(), CompareBackendIds.JAVA));
    }

    private static ColumnOutcome findOutcome(CompareUnitReport report, String backendId) {
        return report.getColumnOutcomes().stream()
                .filter(o -> o.getBackendId().equals(backendId))
                .findFirst().orElseThrow();
    }

    static final class DelegatingColumn implements IEvalBackendColumn {
        private final String backendId;

        DelegatingColumn(String backendId) {
            this.backendId = backendId;
        }

        @Override
        public String getBackendId() {
            return backendId;
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
            return InterpreterBackendColumn.INSTANCE.execute(request);
        }
    }
}
