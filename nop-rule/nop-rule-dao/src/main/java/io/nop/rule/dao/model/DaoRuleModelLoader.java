package io.nop.rule.dao.model;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.tree.TreeIndex;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ResourceVersionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.resource.DaoEntityResource;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.entity.NopRuleNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslParseHelper;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SchemaLoader;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.rule.dao.NopRuleDaoConstants.RULE_STATUS_ACTIVE;
import static io.nop.rule.dao.NopRuleErrors.ARG_PATH;
import static io.nop.rule.dao.NopRuleErrors.ARG_RULE_NAME;
import static io.nop.rule.dao.NopRuleErrors.ERR_RULE_INVALID_DAO_RESOURCE_PATH;
import static io.nop.rule.dao.NopRuleErrors.ERR_RULE_UNKNOWN_RULE_DEFINITION;
import static io.nop.rule.dao.entity._gen._NopRuleDefinition.PROP_NAME_ruleName;
import static io.nop.rule.dao.entity._gen._NopRuleDefinition.PROP_NAME_ruleVersion;
import static io.nop.rule.dao.entity._gen._NopRuleDefinition.PROP_NAME_status;

public class DaoRuleModelLoader implements IResourceObjectLoader<RuleModel> {
    IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public IDaoProvider getDaoProvider() {
        return daoProvider;
    }

    @Override
    public RuleModel loadObjectFromPath(String path) {
        Guard.checkArgument(path.startsWith("resolve-rule:"), "path not startsWith resolve-rule:");
        String subPath = path.substring("resolve-rule:".length());
        List<String> list = StringHelper.split(subPath, '/');
        if (list.size() != 1 || list.size() != 2)
            throw new NopException(ERR_RULE_INVALID_DAO_RESOURCE_PATH)
                    .param(ARG_PATH, path);

        String ruleName = list.get(1);
        Integer ruleVersion = null;
        if (list.size() > 2) {
            ruleVersion = ResourceVersionHelper.getIntegerVersion(list.get(2));
        }
        return loadRule(ruleName, ruleVersion);
    }

    public RuleModel loadRule(String ruleName, Integer ruleVersion) {
        NopRuleDefinition entity = loadRuleDefinition(ruleName, ruleVersion);
        RuleModel model = buildRuleModel(entity);
        String path = DaoEntityResource.makeDaoResource(entity);
        model.setLocation(SourceLocation.fromPath(path));
        return model;
    }

    public NopRuleDefinition loadRuleDefinition(String ruleName, Integer ruleVersion) {
        Guard.notEmpty(ruleName, "ruleName");

        IEntityDao<NopRuleDefinition> dao = daoProvider.daoFor(NopRuleDefinition.class);

        List<TreeBean> filters = new ArrayList<>();
        filters.add(eq(PROP_NAME_ruleName, ruleName));
        filters.add(eq(PROP_NAME_status, RULE_STATUS_ACTIVE));
        if (ruleVersion != null) {
            filters.add(eq(PROP_NAME_ruleVersion, ruleVersion));
        }

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(filters));
        query.addOrderField(PROP_NAME_ruleVersion, true);
        NopRuleDefinition entity = dao.findFirstByQuery(query);

        if (entity == null)
            throw new NopException(ERR_RULE_UNKNOWN_RULE_DEFINITION)
                    .param(ARG_RULE_NAME, ruleName);
        return entity;
    }

    public ISchema buildRuleInputSchema(NopRuleDefinition entity) {
        if (StringHelper.isEmpty(entity.getModelText()))
            return null;

        XNode node = XNodeParser.instance().parseFromText(null, entity.getModelText());
        XNode inputsNode = node.childByTag("inputs");
        if (inputsNode == null || !inputsNode.hasChild()) {
            return null;
        }

        XNode schemaNode = XNode.make("schema");
        XNode propsNode = schemaNode.makeChild("props");
        for (XNode inputNode : inputsNode.getChildren()) {
            XNode propNode = XNode.make("prop");
            propNode.setLocation(inputNode.getLocation());
            propNode.setAttr("name", inputNode.getAttr("name"));
            propNode.setAttr("displayName", inputNode.getAttr("displayName"));
            propNode.setAttr("mandatory", inputNode.getAttr("mandatory"));
            propNode.setAttr("computed", inputNode.getAttr("computed"));
            propNode.setAttr("schema", inputNode.removeChildByTag("schema"));
            propsNode.appendChild(propNode);
        }

        IXDefinition xdef = SchemaLoader.loadXDefinition(XLangConstants.XDSL_SCHEMA_SCHEMA);
        return (ISchema) new DslModelParser().parseWithXDef(xdef, schemaNode);
    }


    public RuleModel buildRuleModel(NopRuleDefinition entity) {
        XNode node = buildRuleModelNode(entity);
        return compileRuleModel(node);
    }

    private RuleModel compileRuleModel(XNode node) {
        return (RuleModel) new DslModelParser().parseFromNode(node);
    }

    public XNode buildRuleModelNode(NopRuleDefinition entity) {
        XNode node;
        if (StringHelper.isEmpty(entity.getModelText())) {
            node = XNode.make("rule");
        } else {
            node = XNodeParser.instance().parseFromText(null, entity.getModelText());
        }

        node.setAttr("displayName", entity.getDisplayName());
        node.setAttr(XDslKeys.DEFAULT.SCHEMA, RuleConstants.XDSL_SCHEMA_RULE);

//        XNode inputs = node.makeChild("inputs");
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
//    private XNode buildInputNode(NopRuleInput input) {
//        XNode node = XNode.make("input");
//        node.setAttr("name", input.getName());
//        node.setAttr("displayName", input.getDisplayName());
//        node.makeChild("description").content(input.getDescription());
//        node.makeChild("defaultExpr").content(input.getDefaultValue());
//        node.setAttr("computed", Boolean.TRUE.equals(input.getIsComputed()));
//        node.setAttr("mandatory", Boolean.TRUE.equals(input.getIsMandatory()));
//
//        if (!StringHelper.isBlank(input.getSchema())) {
//            XNode schema = XDslParseHelper.parseSchema(null, input.getSchema());
//            node.appendChild(schema);
//        }
//        return node;
//    }
//
//    private XNode buildOutputNode(NopRuleOutput output) {
//        XNode node = XNode.make("output");
//        node.setAttr("name", output.getName());
//        node.setAttr("displayName", output.getDisplayName());
//        node.makeChild("description").content(output.getDescription());
//        node.makeChild("defaultExpr").content(output.getDefaultValue());
//
//        if (!StringHelper.isBlank(output.getSchema())) {
//            XNode schema = XDslParseHelper.parseSchema(null, output.getSchema());
//            node.appendChild(schema);
//        }
//        return node;
//    }
}
