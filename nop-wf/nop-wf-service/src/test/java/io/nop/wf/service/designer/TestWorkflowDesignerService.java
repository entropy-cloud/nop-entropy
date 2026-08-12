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
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.dao.entity.NopWfDefinition;
import io.nop.wf.service.AbstractWorkflowTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_DEFINITION_PUBLISHED;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_DUPLICATE_STEP_NAME;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_INVALID_DOCUMENT;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_MODEL_INVALID;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_UNKNOWN_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * 端到端验证（plan 338 Phase 3 Rule 22）：空定义 → load → 编辑 → save → reload 一致 → 引擎启动实例 + 迁移。
     *
     * 这是从用户入口点（designer page）到引擎执行的完整链路验证，非组件级单测。
     * codec 层等价验证见 {@link TestWfGraphDocumentCodec#testEditFlowEngineLoadable}。
     */
    @Test
    public void testEndToEnd_loadSaveReload_engineStartInstance() {
        // 1. 以可启动的工作流模型为起点（含 assignment，引擎可加载可启动）
        createDefinition(WF_DEF_ID, WF_NAME, STARTABLE_MODEL_TEXT, 0);

        // 2. loadDesignerPage → 设计器图中可见步骤 s1 / s2
        Map<String, Object> schema1 = run(() -> designerService.loadDesignerPage(WF_DEF_ID));
        Map<String, Object> doc1 = map(schema1.get("document"));
        List<Map<String, Object>> nodes1 = nodeList(doc1);
        assertTrue(nodes1.stream().anyMatch(n -> "s1".equals(n.get("id"))), "step s1 must be visible in graph");
        assertTrue(nodes1.stream().anyMatch(n -> "s2".equals(n.get("id"))), "step s2 must be visible in graph");

        // 3. saveDocument（零编辑保存：codec 必须保留 assignment + transition，模型语义不变）
        run(() -> {
            Map<String, Object> result = designerService.saveDocument(
                    WF_DEF_ID, JsonTool.serialize(doc1, false), null);
            assertEquals(Boolean.TRUE, result.get("ok"));
            return null;
        });

        // 4. reload → 结果一致（s1 / s2 仍在，assignment 经引擎加载路径可解析）
        Map<String, Object> schema2 = run(() -> designerService.loadDesignerPage(WF_DEF_ID));
        Map<String, Object> doc2 = map(schema2.get("document"));
        Set<String> ids2 = nodeList(doc2).stream().map(n -> str(n.get("id"))).collect(Collectors.toSet());
        assertTrue(ids2.contains("s1"), "reloaded document must contain s1");
        assertTrue(ids2.contains("s2"), "reloaded document must contain s2");

        // 4.5 发布定义（设计器仅编辑草稿，引擎仅启动已发布定义）
        publishDefinition(WF_DEF_ID);

        // 5. 引擎可用性证明：从保存后的模型启动实例
        String wfId = startWorkflow(WF_NAME, "test001", null);
        assertNotNull(wfId, "engine must start an instance from the designer-saved model");

        // 6. start 后起始步 s1 自动 complete，工作流应推进到 s2
        List<? extends IWorkflowStep> activeSteps = run(() -> getActivatedSteps(wfId));
        assertFalse(activeSteps.isEmpty(), "workflow must have activated steps after start");
        assertTrue(activeSteps.stream().anyMatch(s -> "s2".equals(s.getStepName())),
                "after start, s2 should be the active step. Actual active steps: "
                        + activeSteps.stream().map(IWorkflowStep::getStepName).collect(Collectors.toList()));

        // 7. 完成 s2 → 工作流结束（至少一次迁移的端到端证明）
        executeTask(wfId, "test002", "s2");
        run(() -> {
            IWorkflow wf = workflowManager.getWorkflow(wfId);
            assertTrue(wf.isEnded(), "workflow should be ended after completing s2");
            return null;
        });
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

    private void publishDefinition(String wfDefId) {
        run(() -> {
            NopWfDefinition def = DaoProvider.instance().daoFor(NopWfDefinition.class).getEntityById(wfDefId);
            def.setStatus(WfDesignerConstants.WF_STATUS_PUBLISHED);
            DaoProvider.instance().daoFor(NopWfDefinition.class).updateEntity(def);
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

    private static String str(Object value) {
        return value == null ? null : value.toString();
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

    private static final String STARTABLE_MODEL_TEXT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                    + "<workflow x:schema=\"/nop/schema/wf/wf.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"\n"
                    + "          wfName=\"test/designer-flow\" wfVersion=\"1\" displayName=\"Designer E2E Flow\">\n"
                    + "    <start startStepName=\"s1\"/>\n"
                    + "    <actions>\n"
                    + "        <action name=\"complete\" displayName=\"完成\" common=\"true\" local=\"true\">\n"
                    + "            <transition appState=\"complete\"/>\n"
                    + "        </action>\n"
                    + "    </actions>\n"
                    + "    <steps>\n"
                    + "        <step name=\"s1\" displayName=\"Step One\">\n"
                    + "            <assignment selection=\"auto\">\n"
                    + "                <actors>\n"
                    + "                    <actor actorId=\"test001\" actorType=\"user\" actorModelId=\"actor1\"/>\n"
                    + "                </actors>\n"
                    + "            </assignment>\n"
                    + "            <transition onAppStates=\"complete\">\n"
                    + "                <to-step stepName=\"s2\"/>\n"
                    + "            </transition>\n"
                    + "        </step>\n"
                    + "        <step name=\"s2\" displayName=\"Step Two\">\n"
                    + "            <assignment selection=\"auto\">\n"
                    + "                <actors>\n"
                    + "                    <actor actorId=\"test002\" actorType=\"user\" actorModelId=\"actor1\"/>\n"
                    + "                </actors>\n"
                    + "            </assignment>\n"
                    + "            <transition onAppStates=\"complete\">\n"
                    + "                <to-end/>\n"
                    + "            </transition>\n"
                    + "        </step>\n"
                    + "    </steps>\n"
                    + "</workflow>\n";
}
