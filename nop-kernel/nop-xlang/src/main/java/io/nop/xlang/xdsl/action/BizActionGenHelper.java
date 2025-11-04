package io.nop.xlang.xdsl.action;

import io.nop.api.core.annotations.biz.BizActionArgKind;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public class BizActionGenHelper {
    private static final String TAG_INPUT = "input";
    private static final String TAG_OUTPUT = "output";
    private static final String ATTR_NAME = "name";
    private static final String VAR_RESULT = "RESULT";

    public static List<String> getInputNames(XNode node) {
        List<XNode> inputNodes = node.childrenByTag(TAG_INPUT);
        List<String> ret = new ArrayList<>();
        for (XNode child : inputNodes) {
            String name = child.attrText(ATTR_NAME);
            ret.add(name);
        }
        return ret;
    }

    public static List<String> getOutputNames(XNode node) {
        List<XNode> list = node.childrenByTag(TAG_OUTPUT);
        List<String> ret = new ArrayList<>();
        for (XNode child : list) {
            String name = child.attrText(ATTR_NAME);
            ret.add(name);
        }
        return ret;
    }

    public static XNode buildBizActionFromActionModel(XNode actionNode, IActionModel actionModel,
                                                      BiFunction<List<String>, Boolean, String> sourceBuilder) {
        XNode node = actionNode.cloneInstance();

        if (node.attrText("displayName") == null) {
            node.setAttr("displayName", actionModel.getDisplayName());
        }
        if (node.attrText("description") == null) {
            node.setAttr("description", actionModel.getDescription());
        }

        node.removeChildByTag("arg");

        List<String> argNames = Collections.emptyList();
        if (actionModel.getInputs() != null) {
            argNames = buildActionArgs(node, actionModel);
        }

        boolean useResult = false;
        if (actionModel.getOutputs() != null) {
            XNode retNode = node.makeChild("return");
            retNode.removeChildByTag("schema");
            if (actionModel.getOutput(VAR_RESULT) != null) {
                useResult = true;
                retNode.appendChild(actionModel.getOutput(VAR_RESULT).getSchemaNode());
            } else {
                retNode.appendChild(actionModel.getOutputSchemaNode());
            }
        }

        String source = sourceBuilder.apply(argNames, useResult);
        node.makeChild("source").setContentValue(source);
        return node;
    }

    public static List<String> buildActionArgs(XNode node, IActionBaseModel actionModel) {
        List<String> names = new ArrayList<>();

        for (IActionInputModel input : actionModel.getInputs()) {
            XNode argNode = XNode.make("arg");
            argNode.setAttr("name", input.getName());
            argNode.setAttr("displayName", input.getDisplayName());
            if (input.getDescription() != null) {
                argNode.makeChild("description").setContentValue(input.getDescription());
            }
            argNode.setAttr("type", input.getType());
            argNode.setAttr("mandatory", input.isMandatory());
            if (input.getName().equals(CoreConstants.VAR_SVC_CTX)) {
                argNode.setAttr("kind", BizActionArgKind.ServiceContext.name());
            } else {
                names.add(input.getName());
            }

            XNode schemaNode = input.getSchemaNode();
            if (schemaNode != null)
                argNode.appendChild(schemaNode);
            node.appendChild(argNode);
        }

        XNode selectionNode = XNode.make("arg");
        selectionNode.setAttr("name", "_selection");
        selectionNode.setAttr("type", FieldSelectionBean.class.getName());
        selectionNode.setAttr("kind", BizActionArgKind.FieldSelection);
        node.appendChild(selectionNode);

        if (actionModel.getInput(CoreConstants.VAR_SVC_CTX) == null) {
            XNode argNode = XNode.make("arg");
            argNode.setAttr("name", CoreConstants.VAR_SVC_CTX);
            argNode.setAttr("type", IServiceContext.class.getName());
            argNode.setAttr("kind", BizActionArgKind.ServiceContext.name());
            node.appendChild(argNode);
        }

        return names;
    }
}
