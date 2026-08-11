/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_DUPLICATE_STEP_NAME;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_EMPTY_DOCUMENT;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_RESERVED_STEP_NAME;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_START_EDGE_INVALID;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_UNKNOWN_NODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WfGraphDocumentCodec 双向转换测试：对 nop/wf/examples 全部示例 .xwf 做
 * 零编辑往返（wfToDoc → docToWf）断言零 diff 与语义保留；非法文档 fail-fast。
 */
public class TestWfGraphDocumentCodec extends BaseTestCase {

    private static final List<String> EXAMPLE_PATHS = List.of(
            "reject-withdraw", "cc-notify", "conditional-branch", "timeout-auto", "transfer-delegate",
            "comprehensive-leave", "or-sign", "subprocess/sub-workflow", "subprocess", "inclusive-branch",
            "sequential-approval", "parallel-branch", "simple-approval", "vote-sign", "countersign");

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testRoundTripAllExamples() {
        for (String path : EXAMPLE_PATHS) {
            roundTrip("/nop/wf/examples/" + path + "/v1.xwf");
        }
    }

    @Test
    public void testRoundTripEmptyFlow() {
        XNode node = WfGraphDocumentCodec.newWorkflowNode("test/empty-flow", 1);
        Map<String, Object> doc = WfGraphDocumentCodec.workflowToDocument(node, "test-empty");
        List<Map<String, Object>> nodes = nodeList(doc);
        assertTrue(nodes.size() >= 2, "empty flow must contain start/end synthetic nodes");

        XNode updated = XNodeParser.instance().parseFromText(null, node.xml());
        WfGraphDocumentCodec.updateWorkflowFromDocument(doc, updated);
        assertEquals(node.xml(), updated.xml());
    }

    @Test
    public void testEditFlowSave() {
        XNode node = WfGraphDocumentCodec.newWorkflowNode("test/edit-flow", 1);
        Map<String, Object> doc = WfGraphDocumentCodec.workflowToDocument(node, "test-edit");

        // 新增步骤 a -> b -> end 链
        addNode(doc, "a", "step", "审批A");
        addNode(doc, "b", "step", "审批B");
        addEdge(doc, WfDesignerConstants.NODE_START, "a", "to-step");
        addEdge(doc, "a", "b", "to-step");
        addEdge(doc, "b", WfDesignerConstants.NODE_END, "to-end");

        XNode updated = XNodeParser.instance().parseFromText(null, node.xml());
        WfGraphDocumentCodec.updateWorkflowFromDocument(doc, updated);

        XNode steps = updated.childByTag("steps");
        assertNotNull(steps);
        assertEquals(2, childrenByTag(steps, "step"));
        assertEquals("a", updated.childByTag("start").attrText("startStepName", null));
        assertNotNull(updated.childByTag("steps").childByTag("step").childByTag("transition"));
    }

    @Test
    public void testInvalidDocumentsFailFast() {
        XNode node = WfGraphDocumentCodec.newWorkflowNode("test/invalid-flow", 1);

        // 重复步骤 id
        Map<String, Object> dup = emptyDoc();
        addNode(dup, "a", "step", "A");
        addNode(dup, "a", "step", "A2");
        assertError(dup, node, ERR_WF_DESIGNER_DUPLICATE_STEP_NAME);

        // 保留 id 冲突（步骤命名为 end）
        Map<String, Object> reserved = emptyDoc();
        addNode(reserved, WfDesignerConstants.NODE_END, "step", "bad");
        assertError(reserved, node, ERR_WF_DESIGNER_RESERVED_STEP_NAME);

        // 边引用不存在的节点
        Map<String, Object> unknown = emptyDoc();
        addNode(unknown, "a", "step", "A");
        addEdge(unknown, "a", "not-exists", "to-step");
        assertError(unknown, node, ERR_WF_DESIGNER_UNKNOWN_NODE);

        // start 连到 end（非步骤目标）
        Map<String, Object> startInvalid = emptyDoc();
        addNode(startInvalid, WfDesignerConstants.NODE_START, "start", "开始");
        addNode(startInvalid, "a", "step", "A");
        addNode(startInvalid, WfDesignerConstants.NODE_END, "end", "结束");
        addEdge(startInvalid, WfDesignerConstants.NODE_START, WfDesignerConstants.NODE_END, "to-end");
        assertError(startInvalid, node, ERR_WF_DESIGNER_START_EDGE_INVALID);

        // 空文档
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("nodes", List.of());
        empty.put("edges", List.of());
        assertError(empty, node, ERR_WF_DESIGNER_EMPTY_DOCUMENT);
    }

    private void roundTrip(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        assertTrue(resource.exists(), "example resource must exist: " + path);
        String text = resource.readText();

        XNode original = XNodeParser.instance().parseFromText(null, text);
        String originalXml = original.xml();

        Map<String, Object> doc = WfGraphDocumentCodec.workflowToDocument(original, path);
        List<Map<String, Object>> nodes = nodeList(doc);
        assertFalse(nodes.isEmpty(), "doc must contain nodes: " + path);
        List<Map<String, Object>> edges = edgeList(doc);
        assertNotNull(edges);

        // 零编辑保存：重新解析原文本，套用文档后必须与原文逐字节一致
        XNode updated = XNodeParser.instance().parseFromText(null, text);
        WfGraphDocumentCodec.updateWorkflowFromDocument(doc, updated);
        assertEquals(originalXml, updated.xml(), "zero-edit round-trip must be diff-free: " + path);

        // 语义保留：保存后节点/边集合与转换前一致
        Map<String, Object> doc2 = WfGraphDocumentCodec.workflowToDocument(updated, path);
        assertEquals(nodes.size(), nodeList(doc2).size(), "node count must be preserved: " + path);
        assertEquals(edges.size(), edgeList(doc2).size(), "edge count must be preserved: " + path);
    }

    private void assertError(Map<String, Object> doc, XNode workflow, io.nop.api.core.exceptions.ErrorCode errorCode) {
        NopException e = assertThrows(NopException.class,
                () -> WfGraphDocumentCodec.updateWorkflowFromDocument(doc, workflow));
        assertEquals(errorCode.getErrorCode(), e.getErrorCode());
    }

    private static Map<String, Object> emptyDoc() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("nodes", new java.util.ArrayList<Map<String, Object>>());
        doc.put("edges", new java.util.ArrayList<Map<String, Object>>());
        return doc;
    }

    private static void addNode(Map<String, Object> doc, String id, String type, String label) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("position", Map.of("x", 100, "y", 100));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", label);
        node.put("data", data);
        nodeList(doc).add(node);
    }

    private static void addEdge(Map<String, Object> doc, String source, String target, String type) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", source + "->" + target);
        edge.put("type", type);
        edge.put("source", source);
        edge.put("target", target);
        edgeList(doc).add(edge);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodeList(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("nodes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edgeList(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("edges");
    }

    private static int childrenByTag(XNode node, String tag) {
        int count = 0;
        for (XNode child : node.getChildren()) {
            if (tag.equals(child.getTagName()))
                count++;
        }
        return count;
    }
}
