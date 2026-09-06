/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.execute;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * rule.xdef对afterExecute的契约: 无论规则是否成功匹配，都会执行到这里
 */
public class TestDecoratedExecutableRule {

    static class RecordingAction implements IEvalAction {
        final String name;
        final List<String> sequence;

        RecordingAction(String name, List<String> sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        @Override
        public Object invoke(IEvalContext scope) {
            sequence.add(name);
            return null;
        }
    }

    private IRuleRuntime newRuntime() {
        return new RuleRuntime(null, EvalExprProvider.newEvalScope());
    }

    @Test
    public void testAfterExecuteInvokedOnMatch() {
        List<String> sequence = new ArrayList<>();
        IExecutableRule inner = rt -> {
            sequence.add("rule");
            return true;
        };
        DecoratedExecutableRule rule = new DecoratedExecutableRule(
                new RecordingAction("before", sequence), inner, new RecordingAction("after", sequence));

        IRuleRuntime ruleRt = newRuntime();
        assertTrue(rule.execute(ruleRt));
        assertEquals(List.of("before", "rule", "after"), sequence);
        assertTrue(ruleRt.isRuleMatch());
    }

    @Test
    public void testAfterExecuteInvokedOnMismatch() {
        List<String> sequence = new ArrayList<>();
        IExecutableRule inner = rt -> {
            sequence.add("rule");
            return false;
        };
        DecoratedExecutableRule rule = new DecoratedExecutableRule(
                new RecordingAction("before", sequence), inner, new RecordingAction("after", sequence));

        IRuleRuntime ruleRt = newRuntime();
        assertFalse(rule.execute(ruleRt));
        assertEquals(List.of("before", "rule", "after"), sequence);
        assertFalse(ruleRt.isRuleMatch());
    }

    @Test
    public void testAfterExecuteInvokedOnException() {
        List<String> sequence = new ArrayList<>();
        IExecutableRule inner = rt -> {
            sequence.add("rule");
            throw new IllegalStateException("boom");
        };
        DecoratedExecutableRule rule = new DecoratedExecutableRule(
                new RecordingAction("before", sequence), inner, new RecordingAction("after", sequence));

        IRuleRuntime ruleRt = newRuntime();
        // NopException.adapt对部分异常类型原样抛出，因此这里断言RuntimeException
        assertThrows(RuntimeException.class, () -> rule.execute(ruleRt));
        assertEquals(List.of("before", "rule", "after"), sequence);
    }

    @Test
    public void testNullBeforeAndAfter() {
        IExecutableRule inner = rt -> true;
        DecoratedExecutableRule rule = new DecoratedExecutableRule(null, inner, null);
        assertTrue(rule.execute(newRuntime()));
    }
}
