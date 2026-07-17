package io.nop.rule.core.execute;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRuleExecutionTracing {

    static final IEvalPredicate ALWAYS_TRUE = ctx -> true;

    @Test
    public void testRuleRuntimeTraceContext() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());

        ruleRt.setCollectLogMessage(false);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("age", 25);
        ruleRt.setTraceContext(ctx);
        ruleRt.logMessage("test", "n1", "Node1");
        assertTrue(ruleRt.getLogMessages().isEmpty());

        ruleRt.setCollectLogMessage(true);
        ruleRt.logMessage("test2", "n2", "Node2");
        assertEquals(1, ruleRt.getLogMessages().size());
        assertEquals("test2", ruleRt.getLogMessages().get(0).getMessage());
        assertEquals(ctx, ruleRt.getLogMessages().get(0).getContext());

        ruleRt.setTraceContext(null);
        ruleRt.logMessage("test3", "n3", "Node3");
        assertEquals(2, ruleRt.getLogMessages().size());
        assertNull(ruleRt.getLogMessages().get(1).getContext());
    }

    @Test
    public void testExecutableRuleMessageExpr() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.setCollectLogMessage(true);

        IEvalAction messageExpr = context -> "age=25";
        ExecutableRule rule = new ExecutableRule(null, "n1", "Node1",
                messageExpr, ALWAYS_TRUE, null, null, false);

        assertEquals("age=25", rule.buildMessage(ruleRt, true));

        ExecutableRule ruleNoMsg = new ExecutableRule(null, "n2", "Node2",
                null, ALWAYS_TRUE, null, null, false);

        assertEquals(RuleConstants.MESSAGE_MATCH, ruleNoMsg.buildMessage(ruleRt, true));
        assertEquals(RuleConstants.MESSAGE_MISMATCH, ruleNoMsg.buildMessage(ruleRt, false));
    }

    @Test
    public void testExecutableRuleMessageExprExceptionFallback() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.setCollectLogMessage(true);

        IEvalAction brokenExpr = context -> {
            throw new RuntimeException("intentional failure");
        };
        ExecutableRule rule = new ExecutableRule(null, "n1", "Node1",
                brokenExpr, ALWAYS_TRUE, null, null, false);

        assertEquals(RuleConstants.MESSAGE_MATCH, rule.buildMessage(ruleRt, true));
        assertEquals(RuleConstants.MESSAGE_MISMATCH, rule.buildMessage(ruleRt, false));
    }

    @Test
    public void testExecutableRuleCollectLogMessageFalse() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.setCollectLogMessage(false);

        IEvalAction messageExpr = context -> "should-not-eval";
        ExecutableRule rule = new ExecutableRule(null, "n1", "Node1",
                messageExpr, ALWAYS_TRUE, null, null, false);

        assertEquals(RuleConstants.MESSAGE_MATCH, rule.buildMessage(ruleRt, true));
    }

    @Test
    public void testTraceVarsExecutableRuleCapture() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.getEvalScope().setLocalValue("age", 25);
        ruleRt.getEvalScope().setLocalValue("deptId", "ENG");
        ruleRt.setCollectLogMessage(true);

        Set<String> traceVars = Set.of("age", "deptId");
        IExecutableRule inner = rt -> {
            Map<String, Object> ctx = rt.getTraceContext();
            assertEquals("25", ctx.get("age"));
            assertEquals("ENG", ctx.get("deptId"));
            return true;
        };
        TraceVarsExecutableRule traceRule = new TraceVarsExecutableRule(traceVars, inner);
        traceRule.execute(ruleRt);
    }

    @Test
    public void testTraceVarsExecutableRuleDotPath() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("status", "PENDING");
        ruleRt.getEvalScope().setLocalValue("order", order);
        ruleRt.setCollectLogMessage(true);

        Set<String> traceVars = Set.of("order.status");
        IExecutableRule inner = rt -> {
            Map<String, Object> ctx = rt.getTraceContext();
            assertEquals("PENDING", ctx.get("order.status"));
            return true;
        };
        TraceVarsExecutableRule traceRule = new TraceVarsExecutableRule(traceVars, inner);
        traceRule.execute(ruleRt);
    }

    @Test
    public void testTraceVarsExecutableRuleMissingKey() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.getEvalScope().setLocalValue("age", 25);
        ruleRt.setCollectLogMessage(true);

        Set<String> traceVars = Set.of("age", "nonExistent");
        IExecutableRule inner = rt -> {
            Map<String, Object> ctx = rt.getTraceContext();
            assertEquals("25", ctx.get("age"));
            assertNull(ctx.get("nonExistent"));
            return true;
        };
        TraceVarsExecutableRule traceRule = new TraceVarsExecutableRule(traceVars, inner);
        traceRule.execute(ruleRt);
    }

    @Test
    public void testTraceVarsExecutableRuleCollectLogMessageFalse() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.setCollectLogMessage(false);

        Set<String> traceVars = Set.of("age");
        boolean[] innerCalled = {false};
        IExecutableRule inner = rt -> {
            innerCalled[0] = true;
            assertNull(rt.getTraceContext());
            return true;
        };
        TraceVarsExecutableRule traceRule = new TraceVarsExecutableRule(traceVars, inner);
        traceRule.execute(ruleRt);
        assertTrue(innerCalled[0]);
    }

    @Test
    public void testTraceVarsSanitizeLongValue() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append('x');
        }
        String longStr = sb.toString();
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.getEvalScope().setLocalValue("longVal", longStr);
        ruleRt.setCollectLogMessage(true);

        Set<String> traceVars = Set.of("longVal");
        IExecutableRule inner = rt -> {
            Map<String, Object> ctx = rt.getTraceContext();
            String val = (String) ctx.get("longVal");
            assertEquals(200, val.length());
            return true;
        };
        TraceVarsExecutableRule traceRule = new TraceVarsExecutableRule(traceVars, inner);
        traceRule.execute(ruleRt);
    }

    @Test
    public void testRuleDeciderMessageExpr() {
        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        ruleRt.setCollectLogMessage(true);

        IEvalAction messageExpr = context -> "decider-custom-msg";
        RuleDecider decider = new RuleDecider(null, "d1", "Decider1",
                ALWAYS_TRUE, messageExpr, false, 0, null);

        assertEquals("decider-custom-msg", decider.buildMessage(ruleRt, true));
        assertEquals("decider-custom-msg", decider.buildMessage(ruleRt, false));

        // collectLogMessage=false: fallback to MATCH/MISMATCH
        ruleRt.setCollectLogMessage(false);
        assertEquals(RuleConstants.MESSAGE_MATCH, decider.buildMessage(ruleRt, true));
        assertEquals(RuleConstants.MESSAGE_MISMATCH, decider.buildMessage(ruleRt, false));
    }
}
