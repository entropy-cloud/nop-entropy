/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.model.compile;

import io.nop.api.core.beans.FilterBeans;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.FixedValueEvalAction;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.execute.RuleRuntime;
import io.nop.rule.core.model.RuleDecisionTreeModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.RuleOutputValueModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRuleModelCompiler {

    /**
     * rule.xdef允许没有input定义的规则模型（例如全部为常量条件的规则），应能正常编译执行。
     * Guard.notEmpty对空KeyedList不会抛异常（ApiStringHelper.isEmptyObject只识别null和空String），
     * 本测试固化该行为，防止回归。
     */
    @Test
    public void testCompileRuleWithoutInputs() {
        RuleModel model = new RuleModel();

        RuleDecisionTreeModel root = new RuleDecisionTreeModel();
        RuleDecisionTreeModel child = new RuleDecisionTreeModel();
        child.setId("c1");
        child.setPredicate(io.nop.core.lang.xml.XNode.fromTreeBean(FilterBeans.alwaysTrue()));

        RuleOutputValueModel output = new RuleOutputValueModel();
        output.setName("result");
        output.setValueExpr(new FixedValueEvalAction("ok"));
        child.addOutput(output);

        root.setChildren(List.of(child));
        model.setDecisionTree(root);

        IExecutableRule rule = new RuleModelCompiler().compileRule(model);

        IRuleRuntime ruleRt = new RuleRuntime(null, EvalExprProvider.newEvalScope());
        assertTrue(rule.execute(ruleRt));
        assertEquals("ok", ruleRt.getOutput("result"));
    }
}
