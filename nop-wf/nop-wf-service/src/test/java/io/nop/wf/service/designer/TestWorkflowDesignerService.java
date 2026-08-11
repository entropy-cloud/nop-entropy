/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.DaoProvider;
import io.nop.wf.dao.entity.NopWfDefinition;
import io.nop.wf.service.AbstractWorkflowTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_DEFINITION_PUBLISHED;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_DUPLICATE_STEP_NAME;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_INVALID_DOCUMENT;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_MODEL_INVALID;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_UNKNOWN_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WorkflowDesignerService 集成测试（真实 ORM + DAO 链路）：loadDesignerPage / saveDocument
 * 及保存前引擎加载路径校验（非法文档快速失败不落库）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestWorkflowDesignerService extends AbstractWorkflowTestCase {

    private static final String WF_DEF_ID = "test-designer-flow";
    private static final String WF_NAME = "test/designer-flow";

    @Inject
    WorkflowDesignerService designerService;

    @Test
    public void testLoadDesignerPageEmptyFlow() {
        createDefinition(WF_DEF_ID, WF_NAME, null, 0);
        run(() -> {
            Map<String, Object> schema = designerService.loadDesignerPage(WF_DEF_ID);
            assertEquals("designer-page", schema.get("type"));
            assertNotNull(schema.get("config"), "config required");
            assertNotNull(schema.get("toolbar"), "page-level toolbar region required");

            Map<String, Object> document = map(schema.get("document"));
            List<Map<String, Object>> nodes = nodeList(document);
            assertTrue(nodes.size() >= 2, "empty flow must contain start/end");
            assertTrue(nodes.stream().anyMatch(n -> WfDesignerConstants.NODE_START.equals(n.get("id"))));
            assertTrue(nodes.stream().anyMatch(n -> WfDesignerConstants.NODE_END.equals(n.get("id"))));

            Map<String, Object> toolbar = map(schema.get("toolbar"));
            assertNotNull(toolbar.get("items"), "toolbar items required");
            return null;
        });
    }

    @Test
    public void testLoadDesignerPageFromModelText() {
        createDefinition(WF_DEF_ID, WF_NAME, MODEL_TEXT_2_STEPS, 0);
        run(() -> {
            Map<String, Object> schema = designerService.loadDesignerPage(WF_DEF_ID);
            Map<String, Object> document = map(schema.get("document"));
            List<Map<String, Object>> nodes = nodeList(document);
            assertEquals(4, nodes.size(), "2 steps + start/end");
            assertTrue(nodes.stream().anyMatch(n -> "a".equals(n.get("id"))));
            assertTrue(nodes.stream().anyMatch(n -> "b".equals(n.get("id"))));
            return null;
        });
    }

    @Test
    public void testSaveDocumentPersists() {
        createDefinition(WF_DEF_ID, WF_NAME, null, 0);
        Map<String, Object> doc = buildDocWithSteps();

        run(() -> {
            Map<String, Object> result = designerService.saveDocument(WF_DEF_ID, JsonTool.serialize(doc, false), null);
            assertEquals(Boolean.TRUE, result.get("ok"));
            return null;
        });

        // 落库后：modelText 包含步骤与迁移，且经引擎加载路径可解析（写后复核 validateModel 已通过）
        NopWfDefinition saved = DaoProvider.instance().daoFor(NopWfDefinition.class).getEntityById(WF_DEF_ID);
        assertNotNull(saved.getModelText());
        assertTrue(saved.getModelText().contains("name=\"a\""));
        assertTrue(saved.getModelText().contains("<to-end/>") || saved.getModelText().contains("<to-end />"));
    }

    @Test
    public void testSaveDocumentRejectsUnknownDefinition() {
        NopException e = assertThrows(NopException.class,
                () -> designerService.saveDocument("not-exists", "{}", null));
        assertEquals(ERR_WF_DESIGNER_UNKNOWN_DEFINITION.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSaveDocumentRejectsPublished() {
        createDefinition(WF_DEF_ID, WF_NAME, null, WfDesignerConstants.WF_STATUS_PUBLISHED);
        NopException e = assertThrows(NopException.class,
                () -> designerService.saveDocument(WF_DEF_ID, "{}", null));
        assertEquals(ERR_WF_DESIGNER_DEFINITION_PUBLISHED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSaveDocumentRejectsInvalidJson() {
        createDefinition(WF_DEF_ID, WF_NAME, null, 0);
        NopException e = assertThrows(NopException.class,
                () -> designerService.saveDocument(WF_DEF_ID, "{not-json", null));
        assertEquals(ERR_WF_DESIGNER_INVALID_DOCUMENT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSaveDocumentRejectsDuplicateStep() {
        createDefinition(WF_DEF_ID, WF_NAME, null, 0);
        Map<String, Object> doc = buildDocWithSteps();
        addNode(nodeList(doc), "a", "step", "dup");

        NopException e = assertThrows(NopException.class,
                () -> designerService.saveDocument(WF_DEF_ID, JsonTool.serialize(doc, false), null));
        assertEquals(ERR_WF_DESIGNER_DUPLICATE_STEP_NAME.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSaveDocumentRejectsCycleAndNotPersists() {
        createDefinition(WF_DEF_ID, WF_NAME, null, 0);
        Map<String, Object> doc = buildDocWithSteps();
        // a -> b -> a 构成环（非 backLink）
        addEdge(edgeList(doc), "b", "a", "to-step");

        NopException e = assertThrows(NopException.class,
                () -> designerService.saveDocument(WF_DEF_ID, JsonTool.serialize(doc, false), null));
        assertEquals(ERR_WF_DESIGNER_MODEL_INVALID.getErrorCode(), e.getErrorCode(),
                "cycle must be rejected by engine-path validation");

        // 不落库
        NopWfDefinition saved = DaoProvider.instance().daoFor(NopWfDefinition.class).getEntityById(WF_DEF_ID);
        assertNull(saved.getModelText(), "failed save must not persist modelText");
    }

    private void createDefinition(String wfDefId, String wfName, String modelText, int status) {
        run(() -> {
            NopWfDefinition def = new NopWfDefinition();
            def.setWfDefId(wfDefId);
            def.setWfName(wfName);
            def.setWfVersion(1L);
            def.setDisplayName("Designer Test Flow");
            def.setStatus(status);
            def.setIsDeprecated(false);
            if (modelText != null)
                def.setModelText(modelText);
            DaoProvider.instance().daoFor(NopWfDefinition.class).saveEntity(def);
            return null;
        });
    }

    private Map<String, Object> buildDocWithSteps() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", WF_DEF_ID);
        doc.put("kind", "workflow");

        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        nodes.add(startNode());
        nodes.add(endNode());
        addNode(nodes, "a", "step", "审批A");
        addNode(nodes, "b", "step", "审批B");
        doc.put("nodes", nodes);

        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        addEdge(edges, WfDesignerConstants.NODE_START, "a", "to-step");
        addEdge(edges, "a", "b", "to-step");
        addEdge(edges, "b", WfDesignerConstants.NODE_END, "to-end");
        doc.put("edges", edges);
        return doc;
    }

    private static Map<String, Object> startNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", WfDesignerConstants.NODE_START);
        node.put("type", "start");
        node.put("position", Map.of("x", 60, "y", 60));
        node.put("data", Map.of("label", "开始"));
        return node;
    }

    private static Map<String, Object> endNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", WfDesignerConstants.NODE_END);
        node.put("type", "end");
        node.put("position", Map.of("x", 60, "y", 60));
        node.put("data", Map.of("label", "结束"));
        return node;
    }

    private static void addNode(List<Map<String, Object>> nodes, String id, String type, String label) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("position", Map.of("x", 100, "y", 100));
        node.put("data", Map.of("label", label));
        nodes.add(node);
    }

    private static void addEdge(List<Map<String, Object>> edges, String source, String target, String type) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", source + "->" + target);
        edge.put("type", type);
        edge.put("source", source);
        edge.put("target", target);
        edges.add(edge);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodeList(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("nodes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edgeList(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("edges");
    }

    private static final String MODEL_TEXT_2_STEPS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                    + "<workflow x:schema=\"/nop/schema/wf/wf.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"\n"
                    + "          wfName=\"test/designer-flow\" wfVersion=\"1\" displayName=\"Designer Test Flow\">\n"
                    + "    <start startStepName=\"a\"/>\n"
                    + "    <end/>\n"
                    + "    <steps>\n"
                    + "        <step name=\"a\" displayName=\"审批A\">\n"
                    + "            <transition splitType=\"and\">\n"
                    + "                <to-step stepName=\"b\"/>\n"
                    + "            </transition>\n"
                    + "        </step>\n"
                    + "        <step name=\"b\" displayName=\"审批B\">\n"
                    + "            <transition splitType=\"and\">\n"
                    + "                <to-end/>\n"
                    + "            </transition>\n"
                    + "        </step>\n"
                    + "    </steps>\n"
                    + "</workflow>\n";
}
