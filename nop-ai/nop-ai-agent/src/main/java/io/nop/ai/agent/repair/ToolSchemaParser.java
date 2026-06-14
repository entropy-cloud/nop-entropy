package io.nop.ai.agent.repair;

import io.nop.core.lang.xml.XNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the XDEF attribute-notation schema from {@code AiToolModel.getSchema()}
 * into a parameter-name to bare-type map.
 *
 * <p>The {@code <schema>} XNode has exactly one child element whose tag name is
 * the tool's invocation name (a single root, not {@code <properties>}).
 * Parameters are enumerated from that root's <b>attributes</b> (name = attr
 * name, type = attr value) and <b>child elements</b> (name = tag name, type =
 * body text or {@code xdef:body-type} attribute value).
 *
 * <p>The bare type is the type token with the leading {@code !} (required
 * marker) stripped. For example {@code "!int"} becomes {@code "int"},
 * {@code "full-path"} stays {@code "full-path"}.
 *
 * <p>This parser does NOT use {@code ToolSchemaConverter}, which expects
 * JSON-schema {@code <properties>}/{@code <required>} children and returns null
 * for every real tool in this repo.
 */
final class ToolSchemaParser {

    private ToolSchemaParser() {
    }

    /**
     * Parse the schema XNode into a map of parameter name to bare type name.
     * Returns an empty map if the schema is null, has no children, or cannot be
     * parsed.
     */
    static Map<String, String> parseParameterTypes(XNode schema) {
        Map<String, String> result = new LinkedHashMap<>();
        if (schema == null) {
            return result;
        }

        List<XNode> roots = schema.getChildren();
        if (roots == null || roots.isEmpty()) {
            return result;
        }

        for (XNode root : roots) {
            collectAttributes(root, result);
            collectChildElements(root, result);
        }

        return result;
    }

    private static void collectAttributes(XNode root, Map<String, String> result) {
        Set<String> attrNames = root.getAttrNames();
        if (attrNames == null) {
            return;
        }
        for (String attrName : attrNames) {
            if (isMetadataAttr(attrName)) {
                continue;
            }
            String typeToken = root.attrText(attrName);
            if (typeToken != null && !typeToken.isEmpty()) {
                result.put(attrName, stripRequiredMarker(typeToken));
            }
        }
    }

    private static void collectChildElements(XNode root, Map<String, String> result) {
        List<XNode> children = root.getChildren();
        if (children == null) {
            return;
        }
        for (XNode child : children) {
            String paramName = child.getTagName();
            String bodyType = child.attrText("xdef:body-type");
            if (bodyType != null && !bodyType.isEmpty()) {
                result.put(paramName, stripRequiredMarker(bodyType));
            } else {
                String text = child.getText();
                if (text != null && !text.trim().isEmpty()) {
                    result.put(paramName, stripRequiredMarker(text.trim()));
                } else {
                    result.put(paramName, "");
                }
            }
        }
    }

    private static boolean isMetadataAttr(String name) {
        return name != null && (name.startsWith("xmlns") || name.startsWith("xdef:"));
    }

    private static String stripRequiredMarker(String typeToken) {
        if (typeToken != null && typeToken.startsWith("!")) {
            return typeToken.substring(1);
        }
        return typeToken;
    }
}
