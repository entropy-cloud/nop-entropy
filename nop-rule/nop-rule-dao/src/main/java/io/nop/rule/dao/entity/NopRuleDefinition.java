package io.nop.rule.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.core.lang.xml.XNode;
import io.nop.orm.component.XmlOrmComponent;
import io.nop.rule.dao.NopRuleDaoConstants;
import io.nop.rule.dao.entity._gen._NopRuleDefinition;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.rule.dao.NopRuleDaoConstants.BEFORE_EXECUTE_NAME;
import static io.nop.rule.dao.NopRuleDaoConstants.INPUTS_NAME;
import static io.nop.rule.dao.NopRuleDaoConstants.OUTPUTS_NAME;


@BizObjName("NopRuleDefinition")
public class NopRuleDefinition extends _NopRuleDefinition {
    public NopRuleDefinition() {
    }

    public Set<String> getRoleIds() {
        Set<String> roleIds = new HashSet<>();
        for (NopRuleRole ruleRole : getRuleRoles()) {
            roleIds.add(ruleRole.getRoleId());
        }
        return roleIds;
    }

    private XNode makeModelNode() {
        XmlOrmComponent component = getModelTextXmlComponent();
        return component.makeNode(NopRuleDaoConstants.RULE_TAG_NAME);
    }

    public List<Map<String, Object>> getRuleInputs() {
        XNode node = getModelTextXmlComponent().getNode();
        if (node == null)
            return null;

        XNode inputsNode = node.childByTag(INPUTS_NAME);
        if (inputsNode == null)
            return null;

        IObjMeta objMeta = SchemaLoader.loadXMeta(NopRuleDaoConstants.XDEF_PATH_RULE);
        IObjPropMeta propMeta = objMeta.getProp(INPUTS_NAME);

        return (List<Map<String, Object>>) DslModelHelper.dslJsonToNode(propMeta.getSchema(), inputsNode);
    }

    public void setRuleInputs(List<Map<String, Object>> ruleInputs) {
        IObjMeta objMeta = SchemaLoader.loadXMeta(NopRuleDaoConstants.XDEF_PATH_RULE);
        IObjPropMeta propMeta = objMeta.getProp(INPUTS_NAME);
        List<XNode> list = DslModelHelper.dslJsonListToNodeList(propMeta.getSchema(), ruleInputs);
        if (list != null) {
            XNode inputsNode = makeModelNode().makeChild(INPUTS_NAME);
            inputsNode.appendChildren(list);
        }
    }

    public List<Map<String, Object>> getRuleOutputs() {
        XNode node = getModelTextXmlComponent().getNode();
        if (node == null)
            return null;

        XNode outputsNode = node.childByTag(OUTPUTS_NAME);
        if (outputsNode == null)
            return null;

        IObjMeta objMeta = SchemaLoader.loadXMeta(NopRuleDaoConstants.XDEF_PATH_RULE);
        IObjPropMeta propMeta = objMeta.getProp(OUTPUTS_NAME);

        return (List<Map<String, Object>>) DslModelHelper.dslJsonToNode(propMeta.getSchema(), outputsNode);

    }

    public void setRuleOutputs(List<Map<String, Object>> ruleOutputs) {
        IObjMeta objMeta = SchemaLoader.loadXMeta(NopRuleDaoConstants.XDEF_PATH_RULE);
        IObjPropMeta propMeta = objMeta.getProp(OUTPUTS_NAME);
        List<XNode> list = DslModelHelper.dslJsonListToNodeList(propMeta.getSchema(), ruleOutputs);
        if (list != null) {
            XNode outputsNode = makeModelNode().makeChild(OUTPUTS_NAME);
            outputsNode.appendChildren(list);
        }
    }

    public String getBeforeExecute() {
        XNode node = getModelTextXmlComponent().getNode();
        if (node == null)
            return null;

        XNode beforeExecute = node.childByTag(BEFORE_EXECUTE_NAME);
        if (beforeExecute == null)
            return null;

        return beforeExecute.bodyFullXml();
    }

    public void setBeforeExecute(String value) {

    }
}
