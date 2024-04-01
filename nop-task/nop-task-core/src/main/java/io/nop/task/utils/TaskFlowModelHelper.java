package io.nop.task.utils;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.task.TaskConstants;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskInputModel;
import io.nop.task.model.TaskOutputModel;
import io.nop.task.model.TaskStepModel;

import java.util.HashMap;
import java.util.List;

public class TaskFlowModelHelper {
    public static TaskFlowModel loadTaskFlowModel(String taskName, Long taskVersion) {
        String path = ResourceVersionHelper.buildResolvePath(TaskConstants.MODEL_TYPE_TASK, taskName, taskVersion);
        return (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    public static XNode getInputsSchemaNode(TaskStepModel stepModel) {
        List<TaskInputModel> inputs = stepModel.getInputs();
        if (inputs.isEmpty())
            return null;

        XNode node = XNode.make("schema");
        XNode props = node.makeChild("props");
        for (TaskInputModel inputModel : inputs) {
            XNode prop = XNode.make("prop");
            prop.setAttr("name", inputModel.getName());
            prop.setAttr("displayName", inputModel.getDisplayName());
            prop.setAttr("type", inputModel.getType());
            if (inputModel.isMandatory())
                prop.setAttr("mandatory", inputModel.isMandatory());

            if (inputModel.getSchema() != null) {
                prop.appendChild(inputModel.getSchema().toNode(new HashMap<>()));
            }
            props.appendChild(prop);
        }
        return node;
    }

    public static XNode getOutputsSchemaNode(TaskStepModel stepModel) {
        List<TaskOutputModel> outputs = stepModel.getOutputs();
        if (outputs.isEmpty())
            return null;

        XNode node = XNode.make("schema");
        XNode props = node.makeChild("props");
        for (TaskOutputModel outputModel : outputs) {
            XNode prop = XNode.make("prop");
            prop.setAttr("name", outputModel.getName());
            prop.setAttr("displayName", outputModel.getDisplayName());
            prop.setAttr("type", outputModel.getType());
            if (outputModel.getSchema() != null) {
                prop.appendChild(outputModel.getSchema().toNode(new HashMap<>()));
            }
            props.appendChild(prop);
        }

        return node;
    }

}