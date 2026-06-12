package io.nop.ai.agent.engine;

import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolSchemaConverter {

    public static Map<String, Object> convert(XNode schema) {
        if (schema == null)
            return null;

        try {
            return doConvert(schema);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> doConvert(XNode schema) {
        Map<String, Object> result = new LinkedHashMap<>();

        String type = schema.attrText("type");
        if (type != null) {
            result.put("type", type);
        }

        String description = schema.attrText("description");
        if (description != null) {
            result.put("description", description);
        }

        XNode propertiesNode = schema.childByTag("properties");
        if (propertiesNode != null) {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (XNode propNode : propertiesNode.getChildren()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                String propType = propNode.attrText("type");
                if (propType != null) {
                    prop.put("type", propType);
                }
                String propDesc = propNode.attrText("description");
                if (propDesc != null) {
                    prop.put("description", propDesc);
                }

                XNode itemsNode = propNode.childByTag("items");
                if (itemsNode != null) {
                    Map<String, Object> items = new LinkedHashMap<>();
                    String itemsType = itemsNode.attrText("type");
                    if (itemsType != null) {
                        items.put("type", itemsType);
                    }
                    if (!items.isEmpty()) {
                        prop.put("items", items);
                    }
                }

                XNode enumNode = propNode.childByTag("enum");
                if (enumNode != null) {
                    List<Object> enumValues = new ArrayList<>();
                    for (XNode val : enumNode.getChildren()) {
                        String valText = val.getText();
                        if (valText != null) {
                            enumValues.add(valText);
                        }
                    }
                    if (!enumValues.isEmpty()) {
                        prop.put("enum", enumValues);
                    }
                }

                if (!prop.isEmpty()) {
                    properties.put(propNode.getTagName(), prop);
                }
            }
            if (!properties.isEmpty()) {
                result.put("properties", properties);
            }
        }

        XNode requiredNode = schema.childByTag("required");
        if (requiredNode != null) {
            List<String> required = new ArrayList<>();
            for (XNode req : requiredNode.getChildren()) {
                String text = req.getText();
                if (text != null && !text.isEmpty()) {
                    required.add(text);
                }
            }
            if (!required.isEmpty()) {
                result.put("required", required);
            }
        }

        return result.isEmpty() ? null : result;
    }
}
