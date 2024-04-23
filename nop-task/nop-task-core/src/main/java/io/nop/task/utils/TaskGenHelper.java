package io.nop.task.utils;

import io.nop.api.core.annotations.biz.BizActionArgKind;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.xml.XNode;
import io.nop.task.TaskConstants;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskInputModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TaskGenHelper {
    public static List<String> getInputNames(XNode node) {
        List<XNode> inputNodes = node.childrenByTag(TaskConstants.TAG_INPUT);
        List<String> ret = new ArrayList<>();
        for (XNode child : inputNodes) {
            String name = child.attrText(TaskConstants.ATTR_NAME);
            ret.add(name);
        }
        return ret;
    }

    public static List<String> getOutputNames(XNode node) {
        List<XNode> list = node.childrenByTag(TaskConstants.TAG_OUTPUT);
        List<String> ret = new ArrayList<>();
        for (XNode child : list) {
            String name = child.attrText(TaskConstants.ATTR_NAME);
            ret.add(name);
        }
        return ret;
    }

    public static XNode buildBizActionFromTaskModel(XNode actionNode, TaskFlowModel taskFlowModel) {
        XNode node = actionNode.cloneInstance();

        if (node.attrText("displayName") == null) {
            node.setAttr("displayName", taskFlowModel.getDisplayName());
        }
        if (node.attrText("description") == null) {
            node.setAttr("description", taskFlowModel.getDescription());
        }

        node.removeChildByTag("arg");

        List<String> argNames = Collections.emptyList();
        if (taskFlowModel.getInputs() != null) {
            argNames = buildActionArgs(node, taskFlowModel);
        }

        if (taskFlowModel.getOutputs() != null) {
            XNode retNode = node.makeChild("return");
            retNode.removeChildByTag("schema");
            retNode.appendChild(taskFlowModel.getOutputSchemaNode());
        }

        String source = "\nconst taskFlowManager = inject('nopTaskFlowManager');\n" +
                "const task = taskFlowManager.getTask('" + taskFlowModel.getName() +
                "'," + taskFlowModel.getVersion() + ");\n" +
                "const taskRt = taskFlowManager.newTaskRuntime(task,"
                + taskFlowModel.isDefaultSaveState() + ",svcCtx);\n"
                + argNames.stream().map(argName->"taskRt.setInput('"+argName+"',"+argName+");").collect(Collectors.joining("\n"))+
                "return task.executeAsync(taskRt,_selection?.sourceFields);\n";
        node.makeChild("source").setContentValue(source);
        return node;
    }

    private static List<String> buildActionArgs(XNode node, TaskFlowModel taskFlowModel) {
        List<String> names = new ArrayList<>();

        for (TaskInputModel input : taskFlowModel.getInputs()) {
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

        if (taskFlowModel.getInput(CoreConstants.VAR_SVC_CTX) == null) {
            XNode argNode = XNode.make("arg");
            argNode.setAttr("name", CoreConstants.VAR_SVC_CTX);
            argNode.setAttr("type", IServiceContext.class.getName());
            argNode.setAttr("kind", BizActionArgKind.ServiceContext.name());
            node.appendChild(argNode);
        }

        return names;
    }
}