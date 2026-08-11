/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.wf.service.designer.NopWfDesignerErrors.ARG_DETAIL;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ARG_NODE_ID;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ARG_STEP_NAME;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_DUPLICATE_STEP_NAME;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_EMPTY_DOCUMENT;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_INVALID_DOCUMENT;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_RESERVED_STEP_NAME;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_START_EDGE_INVALID;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_UNKNOWN_NODE;
import static io.nop.wf.service.designer.WfDesignerConstants.DOC_KIND;
import static io.nop.wf.service.designer.WfDesignerConstants.EDGE_TYPE_TO_EMPTY;
import static io.nop.wf.service.designer.WfDesignerConstants.EDGE_TYPE_TO_END;
import static io.nop.wf.service.designer.WfDesignerConstants.EDGE_TYPE_TO_STEP;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_EMPTY;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_END;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_START;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_TYPE_END;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_TYPE_START;
import static io.nop.wf.service.designer.WfDesignerConstants.NODE_TYPE_STEP;
import static io.nop.wf.service.designer.WfDesignerConstants.SPECIAL_TYPE_VARIANTS;
import static io.nop.wf.service.designer.WfDesignerConstants.STEP_ATTRS;
import static io.nop.wf.service.designer.WfDesignerConstants.TARGET_ATTRS;
import static io.nop.wf.service.designer.WfDesignerConstants.TRANSITION_ATTRS;

/**
 * 工作流模型(XNode) 与 flux GraphDocument(JSON) 的转换器。
 *
 * <p>设计契约（ai-dev/design/nop-wf/workflow-designer-integration.md §3.2）：
 * <ul>
 *     <li>工作在原始 XNode 层：x:extends 头、未识别属性、xpl 片段原样保留；</li>
 *     <li>start/end/empty 为保留 id 的合成节点（见 {@link WfDesignerConstants}），保存时从 .xwf 剔除；</li>
 *     <li>边记录 <transition>：步骤级、步骤 action 级、以及被 ref-actions 引用的工作流级 action 级；
 *         to-assigned 目标不做映射（未匹配即保留，不进入设计图）；</li>
 *     <li>删除语义：只在图中可见的 transition（读取枚举规则枚举到的）随边删除；未枚举到的元素原样保留，
 *         保证往返无数据丢失；</li>
 *     <li>坐标是视图状态，不进入持久化模型。</li>
 * </ul>
 */
public class WfGraphDocumentCodec {

    /**
     * 把 &lt;workflow&gt; 节点转换为设计器文档 JSON。
     *
     * @param docId designer document id（约定为 wfDefId）
     * @return GraphDocument（Map/List 结构，可用 JsonTool 序列化）
     */
    public static Map<String, Object> workflowToDocument(XNode workflow, String docId) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", docId);
        doc.put("kind", DOC_KIND);
        doc.put("name", workflow.attrText("wfName", ""));
        doc.put("version", String.valueOf(workflow.attrText("wfVersion", "0")));

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        Map<String, XNode> stepNodes = stepNodeMap(workflow);
        String startStepName = startStepName(workflow);
        boolean existsStartStep = startStepName != null && stepNodes.containsKey(startStepName);

        Map<String, Object> startNode = new LinkedHashMap<>();
        startNode.put("id", NODE_START);
        startNode.put("type", NODE_TYPE_START);
        startNode.put("position", position(60, 60));
        Map<String, Object> startData = new LinkedHashMap<>();
        startData.put("label", "开始");
        if (existsStartStep)
            startData.put("startStepName", startStepName);
        startNode.put("data", startData);
        nodes.add(startNode);

        Map<String, Object> endNode = new LinkedHashMap<>();
        endNode.put("id", NODE_END);
        endNode.put("type", NODE_TYPE_END);
        endNode.put("position", position(60, 60));
        Map<String, Object> endData = new LinkedHashMap<>();
        endData.put("label", "结束");
        endNode.put("data", endData);
        nodes.add(endNode);

        boolean hasEmptyEdge = false;

        int index = 0;
        Map<String, Object> edgeIds = new LinkedHashMap<>();
        for (Map.Entry<String, XNode> entry : stepNodes.entrySet()) {
            String stepName = entry.getKey();
            XNode step = entry.getValue();

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", stepName);
            node.put("type", nodeTypeOf(step));
            node.put("position", position(140, 160 + index * 140L));
            node.put("data", stepData(step));
            nodes.add(node);

            if ("flow".equals(step.getTagName())) {
                // 子流程元素：直接子级 <transition> 参与图映射
                for (XNode transition : childrenByTag(step, "transition")) {
                    if (EDGE_TYPE_TO_EMPTY.equals(parseTransitionEdge(edges, edgeIds, stepName, transition)))
                        hasEmptyEdge = true;
                }
                index++;
                continue;
            }

            for (XNode transition : childrenByTag(step, "transition")) {
                if (EDGE_TYPE_TO_EMPTY.equals(parseTransitionEdge(edges, edgeIds, stepName, transition)))
                    hasEmptyEdge = true;
            }

            XNode stepActions = step.childByTag("actions");
            if (stepActions != null) {
                for (XNode action : childrenByTag(stepActions, "action")) {
                    for (XNode transition : childrenByTag(action, "transition")) {
                        if (EDGE_TYPE_TO_EMPTY.equals(parseTransitionEdge(edges, edgeIds, stepName, transition)))
                            hasEmptyEdge = true;
                    }
                }
            }

            XNode refActions = step.childByTag("ref-actions");
            if (refActions != null) {
                for (XNode refAction : childrenByTag(refActions, "ref-action")) {
                    String actionName = refAction.attrText("name", null);
                    if (actionName == null)
                        continue;
                    for (XNode action : workflowActions(workflow)) {
                        if (!actionName.equals(action.attrText("name", null)))
                            continue;
                        for (XNode transition : childrenByTag(action, "transition")) {
                            if (EDGE_TYPE_TO_EMPTY.equals(parseTransitionEdge(edges, edgeIds, stepName, transition)))
                                hasEmptyEdge = true;
                        }
                    }
                }
            }
            index++;
        }

        if (existsStartStep) {
            addEdge(edges, edgeIds, NODE_START, startStepName, EDGE_TYPE_TO_STEP, null);
        }

        if (hasEmptyEdge) {
            Map<String, Object> emptyNode = new LinkedHashMap<>();
            emptyNode.put("id", NODE_EMPTY);
            emptyNode.put("type", NODE_TYPE_STEP);
            emptyNode.put("position", position(260, 160 + stepNodes.size() * 140L + 80));
            Map<String, Object> emptyData = new LinkedHashMap<>();
            emptyData.put("label", "空步骤");
            emptyNode.put("data", emptyData);
            nodes.add(emptyNode);
        }

        doc.put("nodes", nodes);
        doc.put("edges", edges);
        return doc;
    }

    /**
     * 把设计器文档 JSON 回写到 &lt;workflow&gt; 节点（原地修改）。
     */
    public static void updateWorkflowFromDocument(Map<String, Object> doc, XNode workflow) {
        if (doc == null)
            throw invalidDoc("document is null");

        List<Map<String, Object>> nodeList = nodeList(doc);
        List<Map<String, Object>> edgeList = edgeList(doc);

        if (nodeList.isEmpty())
            throw new NopException(ERR_WF_DESIGNER_EMPTY_DOCUMENT);

        validateNodes(nodeList);

        Map<String, XNode> stepNodes = stepNodeMap(workflow);
        Map<String, XNode> docStepNodes = new LinkedHashMap<>();
        Set<String> docStepNames = new LinkedHashSet<>();

        XNode stepsNode = workflow.makeChild("steps");

        // 先为文档中所有步骤确保元素存在（新建的步骤在边处理时可被引用）
        for (Map<String, Object> node : nodeList) {
            String id = str(node.get("id"));
            if (isReserved(id))
                continue;
            docStepNames.add(id);
            XNode step = stepNodes.get(id);
            if (step == null) {
                step = XNode.make("step");
                step.setAttr("name", id);
                stepsNode.appendChild(step);
                stepNodes.put(id, step);
            }
            docStepNodes.put(id, step);
        }

        List<String> removedSteps = new ArrayList<>();
        for (String stepName : stepNodes.keySet()) {
            if (!docStepNames.contains(stepName))
                removedSteps.add(stepName);
        }

        // 枚举已有 transition（与读取规则一致，保证删除语义可判定）
        List<TransitionRec> records = enumerateTransitionRecords(workflow, stepNodes, removedSteps);

        for (Map<String, Object> edge : edgeList) {
            String source = str(edge.get("source"));
            String target = str(edge.get("target"));
            String type = edgeTypeOf(edge, target);
            if (source == null || target == null)
                throw invalidDoc("edge source/target is null");
            if (!isNodeId(nodeList, source))
                throw new NopException(ERR_WF_DESIGNER_UNKNOWN_NODE).param(ARG_NODE_ID, source);
            if (!isNodeId(nodeList, target))
                throw new NopException(ERR_WF_DESIGNER_UNKNOWN_NODE).param(ARG_NODE_ID, target);

            Map<String, Object> data = map(edge.get("data"));

            if (NODE_START.equals(source)) {
                if (!EDGE_TYPE_TO_STEP.equals(type))
                    throw new NopException(ERR_WF_DESIGNER_START_EDGE_INVALID).param(ARG_NODE_ID, target);
                workflow.childByTag("start").setAttr("startStepName", target);
                continue;
            }
            if (isReserved(source)) {
                // NODE_END / NODE_EMPTY 没有出边
                throw new NopException(ERR_WF_DESIGNER_UNKNOWN_NODE).param(ARG_NODE_ID, source);
            }

            TransitionRec matched = null;
            for (TransitionRec rec : records) {
                if (rec.used)
                    continue;
                if (source.equals(rec.source) && type.equals(rec.edgeType) && target.equals(rec.target)) {
                    matched = rec;
                    break;
                }
            }

            if (matched != null) {
                matched.used = true;
                matched.applyAttrs(data);
            } else {
                // 合成：在源步骤下追加步骤级 <transition>
                docStepNodes.get(source).appendChild(buildTransition(type, target, data));
            }
        }

        // 删除未匹配的 transition 元素（保留未枚举到的元素，避免数据丢失）
        for (TransitionRec rec : records) {
            if (!rec.used && !rec.inRemovedStep) {
                rec.transition.detach();
            }
        }

        // 步骤属性回写（含新建步骤）
        for (Map<String, Object> node : nodeList) {
            String id = str(node.get("id"));
            if (isReserved(id))
                continue;
            applyStepData(docStepNodes.get(id), map(node.get("data")));
        }

        for (String removedStep : removedSteps) {
            stepNodes.get(removedStep).detach();
        }

        // startStepName 兜底：无起始边但已有步骤时，指向第一个步骤
        String startStepName = startStepName(workflow);
        if (startStepName == null) {
            if (!docStepNames.isEmpty()) {
                workflow.childByTag("start").setAttr("startStepName", docStepNames.iterator().next());
            }
        }
    }

    /**
     * 创建新的空 &lt;workflow&gt; 节点（modelText 为空时使用）。
     */
    public static XNode newWorkflowNode(String wfName, long wfVersion) {
        XNode workflow = XNode.make("workflow");
        workflow.setAttr("x:schema", "/nop/schema/wf/wf.xdef");
        workflow.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");
        workflow.setAttr("wfName", wfName);
        workflow.setAttr("wfVersion", wfVersion);
        workflow.appendChild(XNode.make("description"));
        XNode start = XNode.make("start");
        start.setAttr("startStepName", "");
        workflow.appendChild(start);
        workflow.appendChild(XNode.make("end"));
        workflow.appendChild(XNode.make("steps"));
        return workflow;
    }

    public static Map<String, Object> emptyDocument(String wfName, long wfVersion, String wfDefId) {
        return workflowToDocument(newWorkflowNode(wfName, wfVersion), wfDefId);
    }

    // ==================== 内部实现 ====================

    static NopException invalidDoc(String detail) {
        return new NopException(ERR_WF_DESIGNER_INVALID_DOCUMENT).param(ARG_DETAIL, detail);
    }

    private static Map<String, XNode> stepNodeMap(XNode workflow) {
        Map<String, XNode> map = new LinkedHashMap<>();
        XNode steps = workflow.childByTag("steps");
        if (steps == null)
            return map;
        // <steps> 可包含 step / join / flow 三种元素（均为 WfStepModel，见 wf.xdef）
        for (String tag : List.of("step", "join", "flow")) {
            for (XNode step : childrenByTag(steps, tag)) {
                String name = step.attrText("name", null);
                if (name != null && !name.isEmpty())
                    map.put(name, step);
            }
        }
        return map;
    }

    private static String startStepName(XNode workflow) {
        XNode start = workflow.childByTag("start");
        if (start == null)
            return null;
        String name = start.attrText("startStepName", null);
        return name == null || name.isEmpty() ? null : name;
    }

    private static String nodeTypeOf(XNode step) {
        // 子流程元素映射到 subworkflow 节点类型（DesignerConfig 内置变体）
        if ("flow".equals(step.getTagName()))
            return WfDesignerConstants.NODE_TYPE_SUBWORKFLOW;
        String specialType = step.attrText("specialType", null);
        if (specialType != null && SPECIAL_TYPE_VARIANTS.contains(specialType))
            return specialType;
        return NODE_TYPE_STEP;
    }

    private static Map<String, Object> stepData(XNode step) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean isPlainStep = "step".equals(step.getTagName());
        for (String attr : STEP_ATTRS) {
            // specialType 只对普通 step 元素生效（join/flow 元素上不存在该属性，写入会破坏模型）
            if (!isPlainStep && "specialType".equals(attr))
                continue;
            Object value = step.getAttr(attr);
            if (value != null)
                data.put(attr, value.toString());
        }
        XNode description = step.childByTag("description");
        if (description != null) {
            // 原样保留文本内容（含缩进/换行），保证零编辑保存零 diff
            String text = description.contentAsString();
            if (text != null && !text.isEmpty()) {
                data.put("description", text);
            }
        }
        // label 为纯视图字段（节点 body 模板 ${data.label} 用），不在白名单内，保存时不会写回
        String label = step.attrText("displayName", null);
        if (label == null || label.isEmpty())
            label = step.attrText("name", null);
        if (label != null)
            data.put("label", label);
        return data;
    }

    private static void applyStepData(XNode step, Map<String, Object> data) {
        boolean isPlainStep = "step".equals(step.getTagName());
        for (String attr : STEP_ATTRS) {
            if (!isPlainStep && "specialType".equals(attr))
                continue;
            if (data.containsKey(attr)) {
                Object value = data.get(attr);
                if (value == null) {
                    if (step.getAttr(attr) != null)
                        step.removeAttr(attr);
                } else {
                    step.setAttr(attr, value.toString());
                }
            }
        }
        if (data.containsKey("description")) {
            Object descValue = data.get("description");
            XNode description = step.childByTag("description");
            if (descValue == null) {
                if (description != null)
                    description.detach();
            } else {
                if (description == null) {
                    description = XNode.make("description");
                    step.appendChild(description);
                }
                description.setContentValue(descValue.toString());
            }
        }
    }

    /**
     * 解析一个 transition 元素为边。返回边类型（to-step/to-end/to-empty），无目标（如 to-assigned）返回 null。
     */
    private static String parseTransitionEdge(List<Map<String, Object>> edges, Map<String, Object> edgeIds,
                                              String source, XNode transition) {
        String edgeType = null;
        String target = null;
        XNode targetNode = null;
        for (XNode child : transition.getChildren()) {
            String tag = child.getTagName();
            if ("to-step".equals(tag)) {
                edgeType = EDGE_TYPE_TO_STEP;
                target = child.attrText("stepName", null);
                targetNode = child;
            } else if ("to-end".equals(tag)) {
                edgeType = EDGE_TYPE_TO_END;
                target = NODE_END;
                targetNode = child;
            } else if ("to-empty".equals(tag)) {
                edgeType = EDGE_TYPE_TO_EMPTY;
                target = NODE_EMPTY;
                targetNode = child;
            }
            if (target != null)
                break;
        }
        if (target == null)
            return null;

        Map<String, Object> data = new LinkedHashMap<>();
        for (String attr : TRANSITION_ATTRS) {
            Object value = transition.getAttr(attr);
            if (value != null)
                data.put(attr, value.toString());
        }
        if (targetNode != null) {
            for (String attr : TARGET_ATTRS) {
                Object value = targetNode.getAttr(attr);
                if (value != null)
                    data.put(attr, value.toString());
            }
        }
        addEdge(edges, edgeIds, source, target, edgeType, data);
        return edgeType;
    }

    private static void addEdge(List<Map<String, Object>> edges, Map<String, Object> edgeIds,
                                String source, String target, String edgeType, Map<String, Object> data) {
        String baseId = source + "->" + target;
        int seq = edgeIds.containsKey(baseId) ? (Integer) edgeIds.get(baseId) : 0;
        seq++;
        edgeIds.put(baseId, seq);

        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", seq == 1 ? baseId : baseId + "-" + seq);
        edge.put("type", edgeType);
        edge.put("source", source);
        edge.put("target", target);
        if (data != null && !data.isEmpty())
            edge.put("data", data);
        edges.add(edge);
    }

    private static List<TransitionRec> enumerateTransitionRecords(XNode workflow, Map<String, XNode> stepNodes,
                                                                  List<String> removedSteps) {
        List<TransitionRec> records = new ArrayList<>();
        XNode wfActions = workflow.childByTag("actions");
        for (Map.Entry<String, XNode> entry : stepNodes.entrySet()) {
            String stepName = entry.getKey();
            XNode step = entry.getValue();
            boolean inRemoved = removedSteps.contains(stepName);

            if ("flow".equals(step.getTagName())) {
                for (XNode transition : childrenByTag(step, "transition")) {
                    addRecords(records, stepName, transition, inRemoved);
                }
                continue;
            }

            for (XNode transition : childrenByTag(step, "transition")) {
                addRecords(records, stepName, transition, inRemoved);
            }
            XNode stepActions = step.childByTag("actions");
            if (stepActions != null) {
                for (XNode action : childrenByTag(stepActions, "action")) {
                    for (XNode transition : childrenByTag(action, "transition")) {
                        addRecords(records, stepName, transition, inRemoved);
                    }
                }
            }
            XNode refActions = step.childByTag("ref-actions");
            if (refActions != null) {
                for (XNode refAction : childrenByTag(refActions, "ref-action")) {
                    String actionName = refAction.attrText("name", null);
                    if (actionName == null || wfActions == null)
                        continue;
                    for (XNode action : childrenByTag(wfActions, "action")) {
                        if (!actionName.equals(action.attrText("name", null)))
                            continue;
                        for (XNode transition : childrenByTag(action, "transition")) {
                            addRecords(records, stepName, transition, inRemoved);
                        }
                    }
                }
            }
        }
        return records;
    }

    private static void addRecords(List<TransitionRec> records, String source, XNode transition, boolean inRemoved) {
        String edgeType = null;
        String target = null;
        XNode targetNode = null;
        for (XNode child : transition.getChildren()) {
            String tag = child.getTagName();
            if ("to-step".equals(tag)) {
                edgeType = EDGE_TYPE_TO_STEP;
                target = child.attrText("stepName", null);
                targetNode = child;
            } else if ("to-end".equals(tag)) {
                edgeType = EDGE_TYPE_TO_END;
                target = NODE_END;
                targetNode = child;
            } else if ("to-empty".equals(tag)) {
                edgeType = EDGE_TYPE_TO_EMPTY;
                target = NODE_EMPTY;
                targetNode = child;
            }
            if (target != null)
                break;
        }
        if (target == null)
            return;
        records.add(new TransitionRec(source, edgeType, target, transition, targetNode, inRemoved));
    }

    private static List<XNode> workflowActions(XNode workflow) {
        XNode actions = workflow.childByTag("actions");
        if (actions == null)
            return List.of();
        return childrenByTag(actions, "action");
    }

    private static List<XNode> childrenByTag(XNode node, String tag) {
        List<XNode> result = new ArrayList<>();
        if (node == null)
            return result;
        for (XNode child : node.getChildren()) {
            if (tag.equals(child.getTagName()))
                result.add(child);
        }
        return result;
    }

    private static Map<String, Object> position(long x, long y) {
        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("x", x);
        pos.put("y", y);
        return pos;
    }

    private static boolean isReserved(String id) {
        return NODE_START.equals(id) || NODE_END.equals(id) || NODE_EMPTY.equals(id);
    }

    private static boolean isNodeId(List<Map<String, Object>> nodeList, String id) {
        for (Map<String, Object> node : nodeList) {
            if (id.equals(str(node.get("id"))))
                return true;
        }
        return false;
    }

    private static void validateNodes(List<Map<String, Object>> nodeList) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> node : nodeList) {
            String id = str(node.get("id"));
            String type = str(node.get("type"));
            if (id == null || id.isEmpty())
                throw invalidDoc("node id is empty");
            if (isReserved(id)) {
                // 仅允许三个保留 id 以设计器约定的签名出现（start/end 边界节点与 empty 空步骤）
                boolean isStart = NODE_START.equals(id) && NODE_TYPE_START.equals(type);
                boolean isEnd = NODE_END.equals(id) && NODE_TYPE_END.equals(type);
                boolean isEmpty = NODE_EMPTY.equals(id) && NODE_TYPE_STEP.equals(type);
                if (!(isStart || isEnd || isEmpty))
                    throw new NopException(ERR_WF_DESIGNER_RESERVED_STEP_NAME).param(ARG_STEP_NAME, id);
                continue;
            }
            if (!ids.add(id))
                throw new NopException(ERR_WF_DESIGNER_DUPLICATE_STEP_NAME).param(ARG_STEP_NAME, id);
        }
    }

    private static String edgeTypeOf(Map<String, Object> edge, String target) {
        String type = str(edge.get("type"));
        if (type != null)
            return type;
        if (NODE_END.equals(target))
            return EDGE_TYPE_TO_END;
        if (NODE_EMPTY.equals(target))
            return EDGE_TYPE_TO_EMPTY;
        return EDGE_TYPE_TO_STEP;
    }

    private static void applyAttrs(XNode node, List<String> attrs, Map<String, Object> data) {
        if (data == null)
            return;
        for (String attr : attrs) {
            if (!data.containsKey(attr))
                continue;
            Object value = data.get(attr);
            if (value == null) {
                if (node.getAttr(attr) != null)
                    node.removeAttr(attr);
            } else {
                node.setAttr(attr, value.toString());
            }
        }
    }

    private static List<Map<String, Object>> nodeList(Map<String, Object> doc) {
        Object value = doc.get("nodes");
        if (!(value instanceof List))
            throw invalidDoc("nodes must be an array");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map))
                throw invalidDoc("node must be an object");
            result.add((Map<String, Object>) item);
        }
        return result;
    }

    private static List<Map<String, Object>> edgeList(Map<String, Object> doc) {
        Object value = doc.get("edges");
        if (!(value instanceof List))
            throw invalidDoc("edges must be an array");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map))
                throw invalidDoc("edge must be an object");
            result.add((Map<String, Object>) item);
        }
        return result;
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map)
            return (Map<String, Object>) value;
        return new LinkedHashMap<>();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static XNode buildTransition(String type, String target, Map<String, Object> data) {
        XNode transition = XNode.make("transition");
        applyAttrs(transition, TRANSITION_ATTRS, data);
        if (EDGE_TYPE_TO_END.equals(type)) {
            transition.appendChild(XNode.make("to-end"));
        } else if (EDGE_TYPE_TO_EMPTY.equals(type)) {
            transition.appendChild(XNode.make("to-empty"));
        } else {
            XNode toStep = XNode.make("to-step");
            toStep.setAttr("stepName", target);
            transition.appendChild(toStep);
        }
        return transition;
    }

    /** transition 元素的枚举快照：用于删除匹配与元素级属性回写。 */
    static class TransitionRec {
        final String source;
        final String edgeType;
        final String target;
        final XNode transition;
        final XNode targetNode;
        final boolean inRemovedStep;
        boolean used;

        TransitionRec(String source, String edgeType, String target, XNode transition, XNode targetNode,
                      boolean inRemovedStep) {
            this.source = source;
            this.edgeType = edgeType;
            this.target = target;
            this.transition = transition;
            this.targetNode = targetNode;
            this.inRemovedStep = inRemovedStep;
        }

        void applyAttrs(Map<String, Object> data) {
            WfGraphDocumentCodec.applyAttrs(transition, TRANSITION_ATTRS, data);
            if (targetNode != null)
                WfGraphDocumentCodec.applyAttrs(targetNode, TARGET_ATTRS, data);
        }
    }
}