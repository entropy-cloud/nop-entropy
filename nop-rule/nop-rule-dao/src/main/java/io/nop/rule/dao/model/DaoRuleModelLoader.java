/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.dao.model;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.tree.TreeIndex;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.core.resource.component.version.VersionedName;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.resource.DaoEntityResource;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.compile.RuleModelCompiler;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.entity.NopRuleNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslParseHelper;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SchemaLoader;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.rule.dao.NopRuleDaoConstants.RULE_STATUS_ACTIVE;
import static io.nop.rule.dao.NopRuleErrors.ARG_RULE_NAME;
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
        VersionedName versionedName = ResourceVersionHelper.parseVersionedName(path,
                RuleConstants.RESOLVE_RULE_NS_PREFIX);

        Long ruleVersion = versionedName.getVersion() > 0 ? versionedName.getVersion() : null;

        RuleModel ruleModel = loadRule(versionedName.getName(), ruleVersion);

        new RuleModelCompiler().compileRule(ruleModel);
        return ruleModel;
    }

    public RuleModel loadRule(String ruleName, Long ruleVersion) {
        NopRuleDefinition entity = loadRuleDefinition(ruleName, ruleVersion);

        ResourceComponentManager.instance().traceDepends(DaoEntityResource.makeDaoResourcePath(entity));

        RuleModel model = buildRuleModel(entity);
        String path = DaoEntityResource.makeDaoResourcePath(entity);
        model.setLocation(SourceLocation.fromPath(path));
        return model;
    }

    public NopRuleDefinition loadRuleDefinition(String ruleName, Long ruleVersion) {
        Guard.notEmpty(ruleName, "ruleName");

        IEntityDao<NopRuleDefinition> dao = daoProvider.daoFor(NopRuleDefinition.class);

        List<TreeBean> filters = new ArrayList<>();
        filters.add(eq(PROP_NAME_ruleName, ruleName));
        filters.add(eq(PROP_NAME_status, RULE_STATUS_ACTIVE));
        if (ruleVersion != null && ruleVersion > 0) {
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
            //propNode.setAttr("computed", inputNode.getAttr("computed"));
            propNode.setAttr("type", inputNode.getAttr("type"));
            XNode inputSchema = inputNode.removeChildByTag("schema");
            if (inputSchema != null) {
                propNode.appendChild(inputSchema.detach());
            }
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
        XNode node = entity.getModelTextXmlComponent().makeNode("rule").cloneInstance();

        node.setAttr("displayName", entity.getDisplayName());
        node.setAttr("ruleVersion", entity.getRuleVersion());
        node.setAttr(XDslKeys.DEFAULT.SCHEMA, RuleConstants.XDSL_SCHEMA_RULE);

        List<NopRuleNode> nodes = new ArrayList<>(entity.getRuleNodes());
        nodes.sort(Comparator.comparing(NopRuleNode::getSortNo));

        if (RuleConstants.ENUM_RULE_TYPE_TREE.equals(entity.getRuleType())) {
            node.removeChildByTag("decisionMatrix");

            TreeIndex<NopRuleNode> index = TreeIndex.buildFromParentId(nodes,
                    NopRuleNode::get_id, NopRuleNode::getParentId);
            XNode tree = node.makeChild("decisionTree");
            buildRuleTree(tree.makeChild("children"), index.getRoots(), index);
        } else {
            node.removeChildByTag("decisionTree");
        }
        return node;
    }

    private void buildRuleTree(XNode children, List<NopRuleNode> nodes, TreeIndex<NopRuleNode> index) {
        for (NopRuleNode node : nodes) {
            XNode child = XNode.make("child");
            child.setAttr("id", node.orm_idString());
            child.setAttr("label", node.getLabel());
            //child.setAttr("multiMatch",node.getMultiMatch());
            XNode predicate = parsePredicate(node.getPredicate());
            if (predicate != null)
                child.appendChild(predicate);

            Map<String, Object> outputs = (Map<String, Object>) JsonTool.parseNonStrict(node.getOutputs());
            if (outputs != null) {
                XNode outputsNode = child.makeChild("outputs");
                for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                    String name = entry.getKey();
                    XNode exprNode = toExprNode(entry.getValue());
                    XNode outputNode = XNode.make("output");
                    outputNode.setAttr("name", name);
                    if (exprNode != null) {
                        exprNode.setTagName("valueExpr");
                        outputNode.appendChild(exprNode);
                    }
                    outputsNode.appendChild(outputNode);
                }
            }

            List<NopRuleNode> nodeChildren = index.getChildren(node);
            if (nodeChildren != null) {
                buildRuleTree(child.makeChild("children"), nodeChildren, index);
            }

            children.appendChild(child);
        }
    }

    private XNode toExprNode(Object value) {
        if (value instanceof String) {
            String str = value.toString().trim();
            if (str.startsWith("<")) {
                return XNodeParser.instance().parseFromText(null, value.toString());
            }
            XNode node = XNode.make("valueExpr");
            node.content(value);
            return node;
        }
        return XNode.fromValue(value);
    }

    private XNode parsePredicate(String predicate) {
        XNode node = XDslParseHelper.parseXJson(null, predicate, null);
        if (node == null)
            return null;

        if (ApiConstants.DUMMY_TAG_NAME.equals(node.getTagName())) {
            XNode ret = XNode.make("predicate");
            ret.appendChild(node);
            return ret;
        } else if (node.getTagName().equals("predicate")) {
            node.clearAttrs();
            return node;
        } else {
            XNode ret = XNode.make("predicate");
            ret.appendChild(node);
            return ret;
        }
    }

}
