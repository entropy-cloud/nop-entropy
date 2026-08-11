/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.wf.service.designer.WfDesignerConstants.EDGE_TYPE_TO_EMPTY;
import static io.nop.wf.service.designer.WfDesignerConstants.EDGE_TYPE_TO_END;
import static io.nop.wf.service.designer.WfDesignerConstants.EDGE_TYPE_TO_STEP;
import static io.nop.wf.service.designer.WfDesignerConstants.INPUT_PORT;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_TYPE_END;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_TYPE_START;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_TYPE_STEP;
import static io.nop.wf.service.designer.WfDesignerConstants.OUTPUT_PORT;
import static io.nop.wf.service.designer.WfDesignerConstants.SPECIAL_TYPE_VARIANTS;

/**
 * DesignerConfig 固定通用模板装配（设计契约 ai-dev/design/nop-wf/workflow-designer-integration.md §3.3）。
 *
 * <p>输出字段严格限定于 flux {@code DesignerConfig} 契约（flow-designer-core 的 types.ts）内字段，
 * 禁止使用未知字段（flux 对未知 schema 字段静默忽略）。字段合法性由
 * {@code TestWfDesignerConfigFixture} 递归断言兜底。
 *
 * <p>拓扑约束：start 仅能指向步骤（start 无 input 端口 + 出边 role 提供 wf-step）、
 * end/empty 为终态（无 output 端口），经 {@code EdgeTypeConfig.match}（sourceRoles/targetRoles）
 * 与 {@code PortConfig.roles} 表达；顶层强制仍由服务端保存校验（WfModelParser + DAG）兜底。
 */
public class WfDesignerConfigBuilder {

    private static final String ROLE_WF_STEP = "wf-step";
    private static final String ROLE_WF_END = "wf-end";

    public static Map<String, Object> buildConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", "1.0");
        config.put("kind", "workflow");
        config.put("nodeTypes", buildNodeTypes());
        config.put("edgeTypes", buildEdgeTypes());
        config.put("palette", buildPalette());
        config.put("features", buildFeatures());
        return config;
    }

    /**
     * page 级 toolbar region schema（DesignerPageSchema.toolbar）：有内容时整体替换内置工具栏，
     * 按钮支持完整 action 链（装配面裁定 2026-08-10，设计文档 §3.5）。
     *
     * @param wfDefId 直接内联进 ajax 参数（服务端装配时已知，无需运行期绑定）
     * @param readOnly true 时省略保存/编辑类按钮（服务端保存校验仍兜底拒绝）
     */
    public static Map<String, Object> buildToolbarSchema(String wfDefId, boolean readOnly) {
        List<Map<String, Object>> items = new ArrayList<>();

        items.add(button("撤销", "rotate-ccw", namedAction("designer:undo")));
        items.add(button("重做", "rotate-cw", namedAction("designer:redo")));
        items.add(button("网格", "grid-3x3", namedAction("designer:toggleGrid")));

        if (!readOnly) {
            Map<String, Object> save = button("保存", "save", buildSaveChain(wfDefId));
            save.put("variant", "default");
            items.add(save);
        }

        Map<String, Object> toolbar = new LinkedHashMap<>();
        toolbar.put("type", "flex");
        toolbar.put("className", "nop-designer-page-toolbar flex flex-wrap items-center gap-2 px-3 py-2");
        toolbar.put("items", items);
        return toolbar;
    }

    private static List<Map<String, Object>> buildSaveChain(String wfDefId) {
        Map<String, Object> saveDocument = new LinkedHashMap<>();
        saveDocument.put("action", "ajax");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("url", "/r/WorkflowDesignerService__saveDocument");
        args.put("method", "POST");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wfDefId", wfDefId);
        // then 分支 evaluationBindings 含 result（上一 action 的 ActionResult）：export 返回文档 JSON 字符串
        data.put("doc", "${result.data}");
        args.put("data", data);
        saveDocument.put("args", args);
        // 保存成功后标记内部 saved 状态（脏状态清除）
        saveDocument.put("then", namedAction("designer:save"));

        List<Map<String, Object>> chain = new ArrayList<>();
        chain.add(namedAction("designer:export"));
        chain.add(saveDocument);
        return chain;
    }

    private static Map<String, Object> namedAction(String action) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("action", action);
        return step;
    }

    private static Map<String, Object> button(String label, String icon, Object onClick) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("type", "button");
        button.put("label", label);
        button.put("icon", icon);
        button.put("onClick", onClick);
        return button;
    }

    private static List<Map<String, Object>> buildNodeTypes() {
        List<Map<String, Object>> nodeTypes = new ArrayList<>();
        nodeTypes.add(startNodeType());
        nodeTypes.add(endNodeType());
        nodeTypes.add(stepNodeType(NODE_TYPE_STEP, "步骤节点", "circle", "#64748b", true));
        for (String variant : SPECIAL_TYPE_VARIANTS) {
            nodeTypes.add(stepNodeType(variant, variant + "步骤", "circle", specialTypeColor(variant), false));
        }
        return nodeTypes;
    }

    private static String specialTypeColor(String specialType) {
        switch (specialType) {
            case "approver":
                return "#3b82f6";
            case "cc":
                return "#a855f7";
            case "notify":
                return "#f59e0b";
            case "route":
                return "#06b6d4";
            case "condition":
                return "#10b981";
            case "script":
                return "#ef4444";
            case "subworkflow":
                return "#8b5cf6";
            default:
                return "#64748b";
        }
    }

    private static Map<String, Object> startNodeType() {
        Map<String, Object> type = baseNodeType(NODE_TYPE_START, "开始节点", "play", "#22c55e");
        type.put("ports", List.of(port(OUTPUT_PORT, "output", "right", List.of(ROLE_WF_STEP), null)));
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("maxInstances", 1);
        constraints.put("allowIncoming", false);
        constraints.put("allowOutgoing", true);
        type.put("constraints", constraints);
        return type;
    }

    private static Map<String, Object> endNodeType() {
        Map<String, Object> type = baseNodeType(NODE_TYPE_END, "结束节点", "flag", "#ef4444");
        type.put("ports", List.of(port(INPUT_PORT, "input", "left", null, List.of(ROLE_WF_END))));
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("maxInstances", 1);
        constraints.put("allowIncoming", true);
        constraints.put("allowOutgoing", false);
        type.put("constraints", constraints);
        return type;
    }

    private static Map<String, Object> stepNodeType(String id, String label, String icon, String color,
                                                    boolean withCreateDialog) {
        Map<String, Object> type = baseNodeType(id, label, icon, color);
        type.put("ports", List.of(
                port(INPUT_PORT, "input", "left", null, List.of(ROLE_WF_STEP)),
                port(OUTPUT_PORT, "output", "right", List.of(ROLE_WF_STEP), null)));

        Map<String, Object> inspector = new LinkedHashMap<>();
        inspector.put("mode", "panel");
        inspector.put("body", stepInspectorBody());
        type.put("inspector", inspector);

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("label", "新步骤");
        type.put("defaults", defaults);

        if (withCreateDialog) {
            Map<String, Object> createDialog = new LinkedHashMap<>();
            createDialog.put("title", "新建步骤");
            createDialog.put("body", createDialogBody());
            type.put("createDialog", createDialog);
        }
        return type;
    }

    /**
     * v1 通用属性表单：仅覆盖 codec 白名单内字段（displayName/description/specialType），
     * 避免 inspector 写回被 codec 静默忽略造成数据丢失（assignment 等高级属性 v1 透传保留）。
     */
    private static Map<String, Object> stepInspectorBody() {
        Map<String, Object> displayName = new LinkedHashMap<>();
        displayName.put("type", "input-text");
        displayName.put("name", "displayName");
        displayName.put("label", "显示名称");

        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "textarea");
        description.put("name", "description");
        description.put("label", "描述");

        Map<String, Object> specialType = new LinkedHashMap<>();
        specialType.put("type", "select");
        specialType.put("name", "specialType");
        specialType.put("label", "步骤类型");
        List<Map<String, Object>> options = new ArrayList<>();
        for (String variant : SPECIAL_TYPE_VARIANTS) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("label", variant);
            option.put("value", variant);
            options.add(option);
        }
        specialType.put("options", options);

        List<Map<String, Object>> body = new ArrayList<>();
        body.add(displayName);
        body.add(description);
        body.add(specialType);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "form");
        form.put("body", body);
        return form;
    }

    private static Map<String, Object> createDialogBody() {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "input-text");
        name.put("name", "name");
        name.put("label", "步骤名");

        Map<String, Object> displayName = new LinkedHashMap<>();
        displayName.put("type", "input-text");
        displayName.put("name", "displayName");
        displayName.put("label", "显示名称");

        List<Map<String, Object>> body = new ArrayList<>();
        body.add(name);
        body.add(displayName);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "form");
        form.put("body", body);
        return form;
    }

    private static Map<String, Object> baseNodeType(String id, String label, String icon, String color) {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("id", id);
        type.put("label", label);
        type.put("icon", icon);
        type.put("body", nodeBody(color));
        type.put("appearance", appearance(color));
        return type;
    }

    private static Map<String, Object> nodeBody(String color) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "text");
        text.put("body", "${data.label}");
        text.put("className", "text-sm font-medium text-gray-900");

        Map<String, Object> container = new LinkedHashMap<>();
        container.put("type", "container");
        container.put("body", List.of(text));

        Map<String, Object> flex = new LinkedHashMap<>();
        flex.put("type", "flex");
        flex.put("className", "flex items-center gap-2 px-3 py-2 bg-white rounded-lg shadow-sm");
        flex.put("items", List.of(container));
        return flex;
    }

    private static Map<String, Object> appearance(String color) {
        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put("borderWidth", 2);
        appearance.put("borderColor", color);
        appearance.put("borderRadius", 8);
        return appearance;
    }

    private static Map<String, Object> port(String id, String direction, String position,
                                            List<String> provides, List<String> accepts) {
        Map<String, Object> port = new LinkedHashMap<>();
        port.put("id", id);
        port.put("direction", direction);
        port.put("position", position);
        if (provides != null || accepts != null) {
            Map<String, Object> roles = new LinkedHashMap<>();
            if (provides != null)
                roles.put("provides", provides);
            if (accepts != null)
                roles.put("accepts", accepts);
            port.put("roles", roles);
        }
        return port;
    }

    private static List<Map<String, Object>> buildEdgeTypes() {
        List<Map<String, Object>> edgeTypes = new ArrayList<>();
        edgeTypes.add(edgeType(EDGE_TYPE_TO_STEP, "迁移", "#94a3b8", ROLE_WF_STEP, ROLE_WF_STEP));
        edgeTypes.add(edgeType(EDGE_TYPE_TO_END, "结束", "#ef4444", ROLE_WF_STEP, ROLE_WF_END));
        edgeTypes.add(edgeType(EDGE_TYPE_TO_EMPTY, "空步骤", "#f59e0b", ROLE_WF_STEP, ROLE_WF_STEP));
        return edgeTypes;
    }

    private static Map<String, Object> edgeType(String id, String label, String color,
                                                String sourceRole, String targetRole) {
        Map<String, Object> edgeType = new LinkedHashMap<>();
        edgeType.put("id", id);
        edgeType.put("label", label);

        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put("stroke", color);
        appearance.put("strokeWidth", 2);
        appearance.put("markerEnd", "arrow");
        edgeType.put("appearance", appearance);

        Map<String, Object> match = new LinkedHashMap<>();
        match.put("sourceRoles", List.of(sourceRole));
        match.put("targetRoles", List.of(targetRole));
        edgeType.put("match", match);
        return edgeType;
    }

    private static Map<String, Object> buildPalette() {
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("searchable", false);

        List<Map<String, Object>> groups = new ArrayList<>();
        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("id", "basic");
        basic.put("label", "基础节点");
        basic.put("nodeTypes", List.of(NODE_TYPE_START, NODE_TYPE_END));

        List<String> stepTypes = new ArrayList<>();
        stepTypes.add(NODE_TYPE_STEP);
        stepTypes.addAll(SPECIAL_TYPE_VARIANTS);
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("id", "steps");
        steps.put("label", "步骤");
        steps.put("nodeTypes", stepTypes);

        groups.add(basic);
        groups.add(steps);
        palette.put("groups", groups);
        return palette;
    }

    private static Map<String, Object> buildFeatures() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("undo", true);
        features.put("redo", true);
        features.put("history", true);
        features.put("clipboard", true);
        features.put("shortcuts", true);
        features.put("grid", true);
        features.put("fitView", true);
        features.put("export", true);
        return features;
    }
}
