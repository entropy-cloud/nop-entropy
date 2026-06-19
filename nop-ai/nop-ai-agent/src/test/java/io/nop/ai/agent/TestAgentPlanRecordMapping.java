package io.nop.ai.agent;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.md.MappingBasedMarkdownParser;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.record_mapping.model.RecordMappingDefinitions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestAgentPlanRecordMapping {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParsePlanStatus() {
        String markdown = "# My Plan\n\n"
                + "- 计划状态: pending\n";

        Map<String, Object> model = parsePlan(markdown);
        assertNotNull(model);
        assertEquals("My Plan", model.get("title"));
        assertEquals(AgentExecStatus.pending.name(), model.get("status"));
    }

    @Test
    public void testParseAllPlanStatusValues() {
        for (AgentExecStatus status : AgentExecStatus.values()) {
            String markdown = "# Plan\n\n- 计划状态: " + status.name() + "\n";
            Map<String, Object> model = parsePlan(markdown);
            assertEquals(status.name(), model.get("status"),
                    "status should be '" + status.name() + "'");
        }
    }

    @Test
    public void testParseGoalAndPurpose() {
        String markdown = "# Plan\n\n"
                + "- 计划目标: achieve something\n"
                + "- 计划概述: this is the purpose\n";

        Map<String, Object> model = parsePlan(markdown);
        assertEquals("achieve something", model.get("goal"));
        assertEquals("this is the purpose", model.get("purpose"));
    }

    @Test
    public void testParsePhasesWithTasks() {
        String markdown = "# Plan\n\n"
                + "- 计划状态: pending\n"
                + "\n## 阶段\n"
                + "\n### Phase 1\n"
                + "\n- 阶段名称: Phase 1\n"
                + "- 阶段状态: running\n"
                + "\n#### 任务\n"
                + "\n##### T001\n"
                + "\n- 任务编号: T001\n"
                + "- 任务标题: Task 1\n"
                + "- 任务状态: running\n";

        Map<String, Object> model = parsePlan(markdown);
        assertEquals(AgentExecStatus.pending.name(), model.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phases = (List<Map<String, Object>>) model.get("phases");
        assertNotNull(phases);
        assertEquals(1, phases.size());

        Map<String, Object> phase = phases.get(0);
        assertEquals("Phase 1", phase.get("name"));
        assertEquals(AgentExecStatus.running.name(), phase.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) phase.get("tasks");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        Map<String, Object> task = tasks.get(0);
        assertEquals("T001", task.get("taskNo"));
        assertEquals("Task 1", task.get("title"));
        assertEquals(AgentExecStatus.running.name(), task.get("status"));
    }

    @Test
    public void testParseEmptyStatus() {
        String markdown = "# Plan\n\n"
                + "- 计划目标: do something\n";

        Map<String, Object> model = parsePlan(markdown);
        assertNotNull(model);
        assertNull(model.get("status"));
    }

    @Test
    public void testDictValidationPassesForValidStatus() {
        String markdown = "# Plan\n\n"
                + "- 计划状态: completed\n";
        RecordMappingConfig config = getMappingConfig();
        MarkdownDocument doc = MarkdownTool.instance().parseFromText(null, markdown);
        MappingBasedMarkdownParser parser = new MappingBasedMarkdownParser(config);
        RecordMappingContext ctx = new RecordMappingContext();
        ctx.setForceUseMap(true);
        Map<String, Object> result = (Map<String, Object>) parser.map(doc.getRootSection(), ctx);
        assertEquals(AgentExecStatus.completed.name(), result.get("status"));
    }

    private Map<String, Object> parsePlan(String markdownText) {
        RecordMappingConfig config = getMappingConfig();
        MarkdownDocument doc = MarkdownTool.instance().parseFromText(null, markdownText);
        MappingBasedMarkdownParser parser = new MappingBasedMarkdownParser(config);
        RecordMappingContext ctx = new RecordMappingContext();
        ctx.setForceUseMap(true);
        ctx.setSkipValidation(true);
        return (Map<String, Object>) parser.map(doc.getRootSection(), ctx);
    }

    private RecordMappingConfig getMappingConfig() {
        RecordMappingDefinitions defs = (RecordMappingDefinitions) ResourceComponentManager.instance()
                .loadComponentModel("/nop/record/mapping/agentPlan.record-mappings.xml");
        return defs.getMapping("Markdown_to_AgentPlanModel");
    }
}
