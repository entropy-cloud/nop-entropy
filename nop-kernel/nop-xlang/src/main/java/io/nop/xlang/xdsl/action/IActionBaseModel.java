package io.nop.xlang.xdsl.action;

import io.nop.core.lang.xml.XNode;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对Action模型的统一抽象。TaskFlowStep和AiPrompt都满足这一模型要求
 */
public interface IActionBaseModel {
    List<? extends IActionInputModel> getInputs();

    default IActionInputModel getInput(String name) {
        return getInputs().stream().filter(o -> o.getName().equals(name)).findFirst().orElse(null);
    }

    default Collection<String> getInputNames() {
        return getInputs().stream().map(IActionInputModel::getName).collect(Collectors.toList());
    }

    List<? extends IActionOutputModel> getOutputs();

    default IActionOutputModel getOutput(String name) {
        return getOutputs().stream().filter(o -> o.getName().equals(name)).findFirst().orElse(null);
    }

    default Collection<String> getOutputNames() {
        return getOutputs().stream().map(IActionOutputModel::getName).collect(Collectors.toList());
    }

    default XNode getOutputSchemaNode() {
        XNode schema = XNode.make("schema");
        XNode props = schema.makeChild("props");
        if (getOutputs() != null) {
            for (IActionOutputModel output : getOutputs()) {
                XNode prop = XNode.make("prop");
                prop.setAttr("name", output.getName());
                prop.setAttr("displayName", output.getDisplayName());
                prop.setAttr("type", output.getType());
                XNode propSchema = output.getSchemaNode();
                if (propSchema != null)
                    prop.appendChild(propSchema);
                props.appendChild(prop);
            }
        }
        return schema;
    }
}