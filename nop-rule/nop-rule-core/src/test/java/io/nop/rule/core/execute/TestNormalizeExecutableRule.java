/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.execute;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.KeyedList;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleInputDefineModel;
import io.nop.rule.core.model.RuleOutputDefineModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_INPUT_VAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNormalizeExecutableRule {

    private IRuleRuntime newRuntime() {
        return new RuleRuntime(null, EvalExprProvider.newEvalScope());
    }

    private KeyedList<RuleInputDefineModel> inputDefines(String... names) {
        KeyedList<RuleInputDefineModel> list = new KeyedList<>(RuleInputDefineModel::getName);
        for (String name : names) {
            RuleInputDefineModel define = new RuleInputDefineModel();
            define.setName(name);
            list.add(define);
        }
        return list;
    }

    @Test
    public void testNullInputs() {
        // 请求不携带inputs时runtime的inputs为null，不应抛出NPE
        IExecutableRule inner = rt -> true;
        NormalizeInputExecutableRule rule = new NormalizeInputExecutableRule(null, inputDefines("a"), inner);

        IRuleRuntime ruleRt = newRuntime();
        assertTrue(rule.execute(ruleRt));
    }

    @Test
    public void testUnknownInputVar() {
        IExecutableRule inner = rt -> true;
        NormalizeInputExecutableRule rule = new NormalizeInputExecutableRule(null, inputDefines("a"), inner);

        IRuleRuntime ruleRt = newRuntime();
        ruleRt.setInputs(Map.of("b", 1));
        NopException e = assertThrows(NopException.class, () -> rule.execute(ruleRt));
        assertEquals(ERR_RULE_UNKNOWN_INPUT_VAR.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMandatoryOutputNotCheckedOnMismatch() {
        // 规则未命中是正常返回值，mandatory输出检查不应导致抛异常
        RuleOutputDefineModel output = new RuleOutputDefineModel();
        output.setName("dish");
        output.setMandatory(true);

        NormalizeOutputExecutableRule rule = new NormalizeOutputExecutableRule(List.of(output), rt -> false);

        IRuleRuntime ruleRt = newRuntime();
        assertFalse(rule.execute(ruleRt));
    }

    @Test
    public void testMandatoryOutputStillCheckedOnMatch() {
        // 命中但输出为空时仍然要抛出mandatory检查异常
        RuleOutputDefineModel output = new RuleOutputDefineModel();
        output.setName("dish");
        output.setMandatory(true);

        NormalizeOutputExecutableRule rule = new NormalizeOutputExecutableRule(List.of(output), rt -> true);

        IRuleRuntime ruleRt = newRuntime();
        assertThrows(NopException.class, () -> rule.execute(ruleRt));
    }

    @Test
    public void testMandatoryOutputPassOnMatchWithValue() {
        RuleOutputDefineModel output = new RuleOutputDefineModel();
        output.setName("dish");

        IExecutableRule inner = rt -> {
            rt.setOutput("dish", "Steak");
            return true;
        };
        NormalizeOutputExecutableRule rule = new NormalizeOutputExecutableRule(List.of(output), inner);

        IRuleRuntime ruleRt = newRuntime();
        assertTrue(rule.execute(ruleRt));
        assertEquals("Steak", ruleRt.getOutput("dish"));
    }
}
