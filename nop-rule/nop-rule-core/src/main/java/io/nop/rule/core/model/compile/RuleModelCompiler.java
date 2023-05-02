package io.nop.rule.core.model.compile;

import io.nop.api.core.beans.ITreeBean;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.SeqEvalAction;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.execute.AggregateExecutableRule;
import io.nop.rule.core.execute.ExecutableRule;
import io.nop.rule.core.execute.RuleOutputAction;
import io.nop.rule.core.model.RuleDecisionTreeModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.RuleOutputValueModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

import java.util.ArrayList;
import java.util.List;

public class RuleModelCompiler {
    private final XLangCompileTool compileTool;

    public RuleModelCompiler(XLangCompileTool compileTool) {
        this.compileTool = compileTool;
    }

    public RuleModelCompiler() {
        this(XLang.newCompileTool().allowUnregisteredScopeVar(true));
    }

    public IExecutableRule compileRule(RuleModel ruleModel) {
        IExecutableRule rule;
        if (ruleModel.getDecisionTree() != null) {
            rule = compileNode(ruleModel.getDecisionTree());
        } else {
            rule = null;
        }

        rule = new AggregateExecutableRule(rule, ruleModel.getOutputs());

        ruleModel.setExecutableRule(rule);
        return rule;
    }

    private IExecutableRule compileNode(RuleDecisionTreeModel node) {
        IEvalPredicate compiledPredicate = compilePredicate(node);

        IEvalAction action = compileOutputAction(node);

        List<IExecutableRule> children = null;
        if (node.getChildren() != null) {
            children = new ArrayList<>(node.getChildren().size());
            for (RuleDecisionTreeModel childNode : node.getChildren()) {
                IExecutableRule rule = compileNode(childNode);
                children.add(rule);
            }
        }

        return new ExecutableRule(node.getLocation(), node.getId(), node.getLabel(),
                compiledPredicate, action, children, node.isMultiMatch());
    }

    private IEvalPredicate compilePredicate(RuleDecisionTreeModel node) {
        ITreeBean filter = node.getPredicate();
        if (filter == null)
            return IEvalPredicate.ALWAYS_TRUE;

        return new FilterBeanToPredicateTransformer(compileTool).visit(filter, compileTool.getScope());
    }

    private IEvalAction compileOutputAction(RuleDecisionTreeModel node) {
        if (node.getOutputs() == null || node.getOutputs().isEmpty())
            return null;

        if (node.getOutputs().size() == 1)
            return compileOutputAction(node.getOutputs().get(0));

        IEvalAction[] actions = new IEvalAction[node.getOutputs().size()];
        for (int i = 0, n = node.getOutputs().size(); i < n; i++) {
            actions[i] = compileOutputAction(node.getOutputs().get(i));
        }
        return new SeqEvalAction(actions);
    }

    private IEvalAction compileOutputAction(RuleOutputValueModel outputModel) {
        return new RuleOutputAction(outputModel.getName(), outputModel.getValueExpr());
    }
}
