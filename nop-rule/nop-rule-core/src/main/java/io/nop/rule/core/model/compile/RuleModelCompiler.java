package io.nop.rule.core.model.compile;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.SeqEvalAction;
import io.nop.core.model.table.CellPosition;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.execute.AggregateExecutableRule;
import io.nop.rule.core.execute.ComputedInputRule;
import io.nop.rule.core.execute.DecoratedExecutableRule;
import io.nop.rule.core.execute.ExecutableMatrixRule;
import io.nop.rule.core.execute.ExecutableRule;
import io.nop.rule.core.execute.RuleDecider;
import io.nop.rule.core.execute.RuleOutputAction;
import io.nop.rule.core.model.RuleDecisionMatrixModel;
import io.nop.rule.core.model.RuleDecisionTreeModel;
import io.nop.rule.core.model.RuleInputDefineModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.RuleOutputValueModel;
import io.nop.rule.core.model.RuleTableCellModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
            rule = compileTree(ruleModel.getDecisionTree());
        } else {
            rule = compileMatrix(ruleModel.getDecisionMatrix());
        }

        List<RuleInputDefineModel> computedVars = ruleModel.getInputs().stream().filter(RuleInputDefineModel::isComputed)
                .collect(Collectors.toList());

        rule = new AggregateExecutableRule(rule, ruleModel.getOutputs());

        if (!computedVars.isEmpty()) {
            rule = new ComputedInputRule(computedVars, rule);
        }

        if (ruleModel.getBeforeExecute() != null || ruleModel.getAfterExecute() != null) {
            rule = new DecoratedExecutableRule(ruleModel.getBeforeExecute(),
                    rule, ruleModel.getAfterExecute());
        }

        ruleModel.setExecutableRule(rule);
        return rule;
    }

    private IExecutableRule compileTree(RuleDecisionTreeModel node) {
        IEvalPredicate compiledPredicate = compilePredicate(node);

        IEvalAction action = compileOutputAction(node.getOutputs());

        List<IExecutableRule> children = null;
        if (node.getChildren() != null) {
            children = new ArrayList<>(node.getChildren().size());
            for (RuleDecisionTreeModel childNode : node.getChildren()) {
                IExecutableRule rule = compileTree(childNode);
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

        try {
            return new FilterBeanToPredicateTransformer(compileTool).visitRoot(filter, compileTool.getScope());
        } catch (NopException e) {
            e.addXplStack("compilePredicate:id=" + node.getId() + ",loc=" + node.getLocation());
            throw e;
        }
    }

    private IEvalAction compileOutputAction(List<RuleOutputValueModel> outputs) {
        if (outputs == null || outputs.isEmpty())
            return null;

        if (outputs.size() == 1)
            return compileOutputAction(outputs.get(0));

        IEvalAction[] actions = new IEvalAction[outputs.size()];
        for (int i = 0, n = outputs.size(); i < n; i++) {
            actions[i] = compileOutputAction(outputs.get(i));
        }
        return new SeqEvalAction(actions);
    }

    private IEvalAction compileOutputAction(RuleOutputValueModel outputModel) {
        return new RuleOutputAction(outputModel.getName(), outputModel.getValueExpr());
    }

    private IExecutableRule compileMatrix(RuleDecisionMatrixModel matrix) {
        RuleDecider rowDecider = compileDecider(matrix.getRowDecider());
        RuleDecider colDecider = compileDecider(matrix.getColDecider());
        boolean multiMatch = rowDecider.isAnyChildMultiMatch() || colDecider.isAnyChildMultiMatch();

        int rowMax = rowDecider.getMaxLeafIndex() + 1;
        int colMax = colDecider.getMaxLeafIndex() + 1;
        Guard.checkArgument(rowMax < 500);
        Guard.checkArgument(colMax < 500);

        IEvalAction[][] outputs = new IEvalAction[rowMax][colMax];
        for (int i = 0; i < rowMax; i++) {
            for (int j = 0; j < colMax; j++) {
                String name = CellPosition.toABString(i, j);
                RuleTableCellModel cell = matrix.getCell(name);
                if (cell == null || CollectionHelper.isEmpty(cell.getOutputs())) {
                    outputs[i][j] = IEvalAction.NULL_ACTION;
                } else {
                    outputs[i][j] = compileOutputAction(cell.getOutputs());
                }
            }
        }

        return new ExecutableMatrixRule(multiMatch, rowDecider, colDecider, outputs);
    }

    private RuleDecider compileDecider(RuleDecisionTreeModel node) {
        IEvalPredicate predicate = this.compilePredicate(node);
        List<RuleDecider> children = null;
        if (node.getChildren() != null) {
            children = new ArrayList<>(node.getChildren().size());
            for (RuleDecisionTreeModel child : node.getChildren()) {
                children.add(compileDecider(child));
            }
        }
        return new RuleDecider(node.getLocation(), node.getId(), node.getLabel(), predicate,
                node.isMultiMatch(), node.getLeafIndex(), children);
    }
}
