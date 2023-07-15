package io.nop.rule.dao.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.tree.TreeIndex;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.entity.NopRuleInput;
import io.nop.rule.dao.entity.NopRuleNode;
import io.nop.rule.dao.entity.NopRuleOutput;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslParseHelper;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static io.nop.rule.dao.NopRuleErrors.ARG_RULE_GROUP;
import static io.nop.rule.dao.NopRuleErrors.ARG_RULE_NAME;
import static io.nop.rule.dao.NopRuleErrors.ERR_RULE_UNKNOWN_RULE_DEFINITION;

public class DaoRuleModelLoader {
    IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public RuleModel loadRule(String ruleGroup, String ruleName) {
        NopRuleDefinition entity = loadRuleDefinition(ruleGroup, ruleName);
        return buildRuleModel(entity);
    }

    public NopRuleDefinition loadRuleDefinition(String ruleGroup, String ruleName) {
        IEntityDao<NopRuleDefinition> dao = daoProvider.daoFor(NopRuleDefinition.class);

        NopRuleDefinition example = new NopRuleDefinition();
        example.setRuleName(ruleName);
        example.setRuleGroup(ruleGroup);
        NopRuleDefinition entity = dao.findFirstByExample(example);
        if (entity == null)
            throw new NopException(ERR_RULE_UNKNOWN_RULE_DEFINITION)
                    .param(ARG_RULE_GROUP, ruleGroup)
                    .param(ARG_RULE_NAME, ruleName);
        return entity;
    }

    private RuleModel buildRuleModel(NopRuleDefinition entity) {
        XNode node = buildRuleModelNode(entity);
        return compileRuleModel(node);
    }

    private RuleModel compileRuleModel(XNode node) {
        return (RuleModel) new DslModelParser().parseFromNode(node);
    }

    public XNode buildRuleModelNode(NopRuleDefinition entity) {
        XNode node = XNode.make("rule");
        node.setAttr("displayName", entity.getDisplayName());
        node.setAttr(XDslKeys.DEFAULT.SCHEMA, RuleConstants.XDSL_SCHEMA_RULE);

        XNode inputs = node.makeChild("inputs");
//        for (NopRuleInput input : entity.getInputs()) {
//            XNode inputNode = buildInputNode(input);
//            inputs.appendChild(inputNode);
//        }
//
//        XNode outputs = node.makeChild("outputs");
//        for (NopRuleOutput output : entity.getOutputs()) {
//            XNode outputNode = buildOutputNode(output);
//            outputs.appendChild(outputNode);
//        }

        List<NopRuleNode> nodes = new ArrayList<>(entity.getRuleNodes());
        nodes.sort(Comparator.comparing(NopRuleNode::getSortNo));

        TreeIndex<NopRuleNode> index = TreeIndex.buildFromParentId(nodes,
                NopRuleNode::getRuleId, NopRuleNode::getParentId);

        XNode tree = node.makeChild("decisionTree");
        buildRuleTree(tree.makeChild("children"), index.getRoots(), index);
        return node;
    }

    private void buildRuleTree(XNode children, List<NopRuleNode> nodes, TreeIndex<NopRuleNode> index) {
        for (NopRuleNode node : nodes) {
            XNode child = XNode.make("child");
            child.setAttr("id", node.getRuleId());
            child.setAttr("label", node.getLabel());
            //child.setAttr("multiMatch",node.getMultiMatch());
            XNode predicate = parsePredicate(node.getPredicate());
            if (predicate != null)
                child.appendChild(predicate);

            List<NopRuleNode> nodeChildren = index.getChildren(node);
            if (nodeChildren != null) {
                buildRuleTree(child.makeChild("children"), nodeChildren, index);
            }

            children.appendChild(child);
        }
    }

    private XNode parsePredicate(String predicate) {
        XNode node = XDslParseHelper.parseXJson(null, predicate, null);
        if (node != null)
            node.setTagName("predicate");
        return node;
    }

    /**
     * <pre>{@code
     * <var name="!var-name" displayName="string" x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     *      xmlns:xdef="/nop/schema/xdef.xdef" xdef:bean-package="io.nop.xlang.xmeta" xdef:name="ObjVarDefineModel">
     *      <description xdef:value="string" />
     *      <defaultExpr xdef:value="xpl" />
     *      <schema xdef:ref="schema.xdef" />
     * </var>
     * }</pre>
     */
    private XNode buildInputNode(NopRuleInput input) {
        XNode node = XNode.make("input");
        node.setAttr("name", input.getName());
        node.setAttr("displayName", input.getDisplayName());
        node.makeChild("description").content(input.getDescription());
        node.makeChild("defaultExpr").content(input.getDefaultValue());
        node.setAttr("computed", Boolean.TRUE.equals(input.getIsComputed()));
        node.setAttr("mandatory", Boolean.TRUE.equals(input.getIsMandatory()));

        if (!StringHelper.isBlank(input.getSchema())) {
            XNode schema = XDslParseHelper.parseSchema(null, input.getSchema());
            node.appendChild(schema);
        }
        return node;
    }

    private XNode buildOutputNode(NopRuleOutput output) {
        XNode node = XNode.make("output");
        node.setAttr("name", output.getName());
        node.setAttr("displayName", output.getDisplayName());
        node.makeChild("description").content(output.getDescription());
        node.makeChild("defaultExpr").content(output.getDefaultValue());

        if (!StringHelper.isBlank(output.getSchema())) {
            XNode schema = XDslParseHelper.parseSchema(null, output.getSchema());
            node.appendChild(schema);
        }
        return node;
    }
}
