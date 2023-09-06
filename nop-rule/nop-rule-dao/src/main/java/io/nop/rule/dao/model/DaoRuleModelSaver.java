package io.nop.rule.dao.model;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.rule.core.model.RuleDecisionTreeModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.RuleOutputValueModel;
import io.nop.rule.dao.NopRuleDaoConstants;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.entity.NopRuleNode;
import io.nop.xlang.api.source.IWithSourceCode;
import io.nop.xlang.xdsl.DslModelHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DaoRuleModelSaver {
    public void saveRuleModel(RuleModel ruleModel, NopRuleDefinition entity) {
        RuleDecisionTreeModel tree = ruleModel.getDecisionTree();
        ruleModel.setDecisionTree(null);

        XNode node = DslModelHelper.dslModelToXNode(NopRuleDaoConstants.XDEF_PATH_RULE, ruleModel);
        entity.setModelText(node.xml());

        if (tree == null) {
            entity.getRuleNodes().clear();
        } else {
            List<RuleDecisionTreeModel> children = tree.getChildren();
            List<NopRuleNode> roots = entity.getRootRuleNodes();
            List<NopRuleNode> newRoots = updateNodes(entity, children, roots);
            Set<NopRuleNode> removedRoots = new HashSet<>(roots);
            removedRoots.removeAll(newRoots);
            newRoots.removeAll(roots);
            entity.getRuleNodes().addAll(newRoots);
            entity.getRuleNodes().removeAll(removedRoots);
        }
    }

    private List<NopRuleNode> updateNodes(NopRuleDefinition entity,
                                          List<RuleDecisionTreeModel> children, Collection<NopRuleNode> nodes) {
        String ruleId = entity.getRuleId();
        if (children == null)
            children = Collections.emptyList();

        if (children.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, NopRuleNode> map = new HashMap<>();
        for (NopRuleNode node : nodes) {
            map.putIfAbsent(node.getPredicate(), node);
        }

        List<NopRuleNode> list = new ArrayList<>();
        int index = 0;
        for (RuleDecisionTreeModel child : children) {
            index++;
            if (child.getPredicate() == null)
                continue;

            String predicate = JsonTool.serialize(child.getPredicate(), false);

            NopRuleNode node = map.get(predicate);
            if (node == null) {
                node = entity.newOrmEntity(NopRuleNode.class, true);
                map.put(predicate, node);
            }
            node.setPredicate(predicate);
            node.setRuleId(ruleId);
            node.setIsLeaf(node.getChildren().isEmpty());
            node.setSortNo(index);
            node.setLabel(child.getLabel());
            node.setOutputs(buildOutputs(child));

            List<NopRuleNode> newChildren = updateNodes(entity, child.getChildren(), node.getChildren());
            node.getChildren().clear();
            node.getChildren().addAll(newChildren);

            list.add(node);
        }

        return list;
    }

    private String buildOutputs(RuleDecisionTreeModel rule) {
        List<RuleOutputValueModel> outputs = rule.getOutputs();
        if (outputs == null || outputs.isEmpty())
            return null;

        Map<String, String> exprs = new LinkedHashMap<>();
        for (RuleOutputValueModel output : outputs) {
            String name = output.getName();
            String expr = getSourceCode(output.getValueExpr());
            exprs.put(name, expr);
        }
        return JsonTool.serialize(exprs, true);
    }

    private String getSourceCode(IEvalAction action) {
        if (action instanceof IWithSourceCode)
            return ((IWithSourceCode) action).getSource();
        return null;
    }
}
