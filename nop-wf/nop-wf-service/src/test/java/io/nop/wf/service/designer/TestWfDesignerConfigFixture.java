/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DesignerConfig 字段合法性 fixture：镜像 flux flow-designer-core types.ts 契约，
 * 递归断言装配输出只含契约内字段（flux 对未知 schema 字段静默忽略，防止字段静默丢失）。
 */
public class TestWfDesignerConfigFixture {

    /** flux DesignerConfig 契约字段（flow-designer-core/src/types.ts，2026-08-10 核对）。 */
    private static final Set<String> DESIGNER_CONFIG_KEYS = Set.of(
            "$schema", "version", "extends", "kind", "nodeTypes", "edgeTypes", "palette", "shell",
            "toolbar", "shortcuts", "features", "rules", "canvas", "hooks", "classAliases",
            "themeStyles", "documentMode", "treeConfig");

    private static final Set<String> NODE_TYPE_KEYS = Set.of(
            "id", "label", "description", "icon", "body", "ports", "appearance", "roles",
            "constraints", "defaults", "tree", "inspector", "createDialog", "quickActions");

    private static final Set<String> EDGE_TYPE_KEYS = Set.of(
            "id", "label", "body", "appearance", "defaults", "inspector", "match");

    private static final Set<String> PORT_KEYS = Set.of(
            "id", "label", "direction", "position", "roles", "maxConnections", "appearance");

    private static final Set<String> ROLE_KEYS = Set.of("provides", "accepts", "rejects");

    private static final Set<String> NODE_CONSTRAINT_KEYS = Set.of(
            "maxInstances", "minInstances", "allowMove", "allowResize", "allowIncoming",
            "allowOutgoing", "maxIncoming", "maxOutgoing");

    private static final Set<String> NODE_APPEARANCE_KEYS = Set.of(
            "className", "borderRadius", "shadow", "borderWidth", "borderColor",
            "borderColorSelected", "minWidth", "minHeight");

    private static final Set<String> EDGE_APPEARANCE_KEYS = Set.of(
            "stroke", "strokeWidth", "strokeStyle", "animated", "markerEnd");

    private static final Set<String> MATCH_KEYS = Set.of("when", "sourceRoles", "targetRoles");

    private static final Set<String> PALETTE_KEYS = Set.of("searchable", "groups");

    private static final Set<String> GROUP_KEYS = Set.of("id", "label", "description", "nodeTypes");

    private static final Set<String> FEATURES_KEYS = Set.of(
            "undo", "redo", "history", "grid", "minimap", "controls", "fitView", "export",
            "shortcuts", "floatingToolbar", "clipboard", "autoLayout", "multiSelect");

    private static final Set<String> INSPECTOR_KEYS = Set.of("mode", "body");

    private static final Set<String> CREATE_DIALOG_KEYS = Set.of("title", "body", "submitAction");

    @Test
    public void testConfigFieldsLegal() {
        Map<String, Object> config = WfDesignerConfigBuilder.buildConfig();
        assertKeys(config, DESIGNER_CONFIG_KEYS, "DesignerConfig");

        assertEquals("1.0", config.get("version"));
        assertEquals("workflow", config.get("kind"));
        assertFalse(config.containsKey("documentMode"), "documentMode must be absent (graph mode)");

        assertKeysList(nodeTypes(config), NODE_TYPE_KEYS, "NodeTypeConfig");
        assertKeysList(edgeTypes(config), EDGE_TYPE_KEYS, "EdgeTypeConfig");
        assertKeys(palette(config), PALETTE_KEYS, "PaletteConfig");
        assertKeys(features(config), FEATURES_KEYS, "DesignerFeatures");

        for (Map<String, Object> nodeType : nodeTypes(config)) {
            if (nodeType.containsKey("ports"))
                assertKeysList(ports(nodeType), PORT_KEYS, "PortConfig");
            if (nodeType.containsKey("constraints"))
                assertKeys(constraints(nodeType), NODE_CONSTRAINT_KEYS, "NodeConstraintConfig");
            if (nodeType.containsKey("appearance"))
                assertKeys(appearance(nodeType), NODE_APPEARANCE_KEYS, "NodeTypeAppearance");
            if (nodeType.containsKey("inspector"))
                assertKeys(inspector(nodeType), INSPECTOR_KEYS, "NodeTypeInspector");
            if (nodeType.containsKey("createDialog"))
                assertKeys(createDialog(nodeType), CREATE_DIALOG_KEYS, "CreateDialogConfig");
            for (Map<String, Object> port : ports(nodeType)) {
                if (port.containsKey("roles"))
                    assertKeys(roles(port), ROLE_KEYS, "PortRoles");
            }
        }

        for (Map<String, Object> edgeType : edgeTypes(config)) {
            if (edgeType.containsKey("appearance"))
                assertKeys(appearance(edgeType), EDGE_APPEARANCE_KEYS, "EdgeTypeAppearance");
            if (edgeType.containsKey("match"))
                assertKeys(match(edgeType), MATCH_KEYS, "EdgeMatchConfig");
        }

        for (Map<String, Object> group : paletteGroups(config)) {
            assertKeys(group, GROUP_KEYS, "PaletteGroupConfig");
        }
    }

    @Test
    public void testNodeTypeBaseline() {
        Map<String, Object> config = WfDesignerConfigBuilder.buildConfig();
        List<Map<String, Object>> nodeTypes = nodeTypes(config);
        assertTrue(nodeTypes.stream().anyMatch(t -> "start".equals(t.get("id"))), "start node type required");
        assertTrue(nodeTypes.stream().anyMatch(t -> "end".equals(t.get("id"))), "end node type required");
        assertTrue(nodeTypes.stream().anyMatch(t -> "step".equals(t.get("id"))), "step node type required");
        for (String variant : WfDesignerConstants.SPECIAL_TYPE_VARIANTS) {
            assertTrue(nodeTypes.stream().anyMatch(t -> variant.equals(t.get("id"))),
                    "specialType variant node type required: " + variant);
        }
    }

    @Test
    public void testToolbarSchemaStructure() {
        Map<String, Object> toolbar = WfDesignerConfigBuilder.buildToolbarSchema("test-wf-def", false);
        assertEquals("flex", toolbar.get("type"));
        List<Map<String, Object>> items = items(toolbar);

        Map<String, Object> save = items.stream()
                .filter(item -> "保存".equals(item.get("label")))
                .findFirst().orElseThrow(() -> new AssertionError("save button missing"));
        Object onClick = save.get("onClick");
        assertTrue(onClick instanceof List, "save button must use action chain");
        List<Map<String, Object>> chain = (List<Map<String, Object>>) onClick;
        assertEquals("designer:export", chain.get(0).get("action"), "chain must start with designer:export");

        Map<String, Object> ajax = chain.get(1);
        assertEquals("ajax", ajax.get("action"), "chain second step must be ajax");
        Map<String, Object> args = map(ajax.get("args"));
        assertTrue(String.valueOf(args.get("url")).endsWith("WorkflowDesignerService__saveDocument"));
        Map<String, Object> data = map(args.get("data"));
        assertEquals("test-wf-def", data.get("wfDefId"));
        assertNotNull(data.get("doc"), "doc param must bind export result");

        Object then = ajax.get("then");
        assertTrue(then instanceof Map, "ajax step must have then branch");
        assertEquals("designer:save", map(then).get("action"), "after save mark designer saved state");

        // 命名按钮（撤销/重做/网格）直接可用
        assertTrue(items.stream().anyMatch(item -> "designer:undo".equals(onClickAction(item))),
                "undo button must be a named action");
    }

    @Test
    public void testReadOnlyToolbarOmitsSave() {
        Map<String, Object> toolbar = WfDesignerConfigBuilder.buildToolbarSchema("test-wf-def", true);
        List<Map<String, Object>> items = items(toolbar);
        assertFalse(items.stream().anyMatch(item -> "保存".equals(item.get("label"))),
                "readOnly toolbar must not contain save button");
    }

    // ==================== 辅助断言 ====================

    private static void assertKeysList(List<Map<String, Object>> list, Set<String> allowed, String type) {
        for (Map<String, Object> map : list)
            assertKeys(map, allowed, type);
    }

    private static void assertKeys(Map<String, Object> map, Set<String> allowed, String type) {
        for (String key : map.keySet()) {
            assertTrue(allowed.contains(key), type + " contains unknown field: " + key);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodeTypes(Map<String, Object> config) {
        return (List<Map<String, Object>>) config.get("nodeTypes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edgeTypes(Map<String, Object> config) {
        return (List<Map<String, Object>>) config.get("edgeTypes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> paletteGroups(Map<String, Object> config) {
        return (List<Map<String, Object>>) map(config.get("palette")).get("groups");
    }

    private static Map<String, Object> palette(Map<String, Object> config) {
        return map(config.get("palette"));
    }

    private static Map<String, Object> features(Map<String, Object> config) {
        return map(config.get("features"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> ports(Map<String, Object> nodeType) {
        Object value = nodeType.get("ports");
        if (!(value instanceof List))
            return List.of();
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> constraints(Map<String, Object> nodeType) {
        return map(nodeType.get("constraints"));
    }

    private static Map<String, Object> appearance(Map<String, Object> type) {
        return map(type.get("appearance"));
    }

    private static Map<String, Object> inspector(Map<String, Object> nodeType) {
        return map(nodeType.get("inspector"));
    }

    private static Map<String, Object> createDialog(Map<String, Object> nodeType) {
        return map(nodeType.get("createDialog"));
    }

    private static Map<String, Object> match(Map<String, Object> edgeType) {
        return map(edgeType.get("match"));
    }

    private static Map<String, Object> roles(Map<String, Object> port) {
        return map(port.get("roles"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> toolbar) {
        return (List<Map<String, Object>>) toolbar.get("items");
    }

    private static Object onClickAction(Map<String, Object> item) {
        Object onClick = item.get("onClick");
        if (onClick instanceof Map)
            return map(onClick).get("action");
        if (onClick instanceof List) {
            List<Map<String, Object>> chain = (List<Map<String, Object>>) onClick;
            return chain.isEmpty() ? null : chain.get(0).get("action");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
