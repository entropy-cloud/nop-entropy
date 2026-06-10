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
                + "- 存储路径: /tmp/plan\n"
                + "- 计划概述: test overview\n"
                + "- 计划状态: pending\n";

        Map<String, Object> model = parsePlan(markdown);
        assertNotNull(model);
        assertEquals("My Plan", model.get("title"));
        assertEquals(AgentExecStatus.pending.name(), model.get("planStatus"));
    }

    @Test
    public void testParseAllPlanStatusValues() {
        for (AgentExecStatus status : AgentExecStatus.values()) {
            String markdown = "# Plan\n\n- 计划状态: " + status.name() + "\n";
            Map<String, Object> model = parsePlan(markdown);
            assertEquals(status.name(), model.get("planStatus"),
                    "planStatus should be '" + status.name() + "'");
        }
    }

    @Test
    public void testParseTaskStatus() {
        String markdown = "# Plan\n\n"
                + "- 计划状态: pending\n"
                + "\n## 任务\n"
                + "\n### T001\n"
                + "\n- 任务编号: T001\n"
                + "- 任务标题: Task 1\n"
                + "- 任务状态: running\n";

        Map<String, Object> model = parsePlan(markdown);
        assertEquals(AgentExecStatus.pending.name(), model.get("planStatus"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) model.get("tasks");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        Map<String, Object> task = tasks.get(0);
        assertEquals("T001", task.get("taskNo"));
        assertEquals("Task 1", task.get("title"));
        assertEquals(AgentExecStatus.running.name(), task.get("status"));
    }

    @Test
    public void testParseAllTaskStatusValues() {
        for (AgentExecStatus status : AgentExecStatus.values()) {
            String markdown = "# Plan\n\n"
                    + "- 计划状态: pending\n"
                    + "\n## 任务\n"
                    + "\n### T-" + status.name() + "\n"
                    + "\n- 任务编号: T-" + status.name() + "\n"
                    + "- 任务标题: Task for " + status.name() + "\n"
                    + "- 任务状态: " + status.name() + "\n";

            Map<String, Object> model = parsePlan(markdown);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) model.get("tasks");
            assertNotNull(tasks, "tasks should not be null for status " + status);
            assertEquals(1, tasks.size());
            assertEquals(status.name(), tasks.get(0).get("status"),
                    "task status should be '" + status.name() + "'");
        }
    }

    @Test
    public void testParseEmptyStatus() {
        String markdown = "# Plan\n\n"
                + "- 存储路径: /tmp/plan\n";

        Map<String, Object> model = parsePlan(markdown);
        assertNotNull(model);
        assertNull(model.get("planStatus"));
    }

    @Test
    public void testParseMultipleTasksWithDifferentStatuses() {
        String markdown = "# Plan\n\n"
                + "- 计划状态: running\n"
                + "\n## 任务\n"
                + "\n### T001\n"
                + "\n- 任务编号: T001\n"
                + "- 任务标题: Done Task\n"
                + "- 任务状态: completed\n"
                + "\n### T002\n"
                + "\n- 任务编号: T002\n"
                + "- 任务标题: Failed Task\n"
                + "- 任务状态: failed\n"
                + "\n### T003\n"
                + "\n- 任务编号: T003\n"
                + "- 任务标题: Pending Task\n"
                + "- 任务状态: pending\n";

        Map<String, Object> model = parsePlan(markdown);
        assertEquals(AgentExecStatus.running.name(), model.get("planStatus"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) model.get("tasks");
        assertEquals(3, tasks.size());
        assertEquals(AgentExecStatus.completed.name(), tasks.get(0).get("status"));
        assertEquals(AgentExecStatus.failed.name(), tasks.get(1).get("status"));
        assertEquals(AgentExecStatus.pending.name(), tasks.get(2).get("status"));
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
        assertEquals(AgentExecStatus.completed.name(), result.get("planStatus"));
    }

    @Test
    public void testTaskStatusFieldIsNamedStatus() {
        String markdown = "# Plan\n\n"
                + "- 计划状态: pending\n"
                + "\n## 任务\n"
                + "\n### T001\n"
                + "\n- 任务编号: T001\n"
                + "- 任务标题: Check\n"
                + "- 任务状态: failed\n";

        Map<String, Object> model = parsePlan(markdown);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) model.get("tasks");
        Map<String, Object> task = tasks.get(0);

        assertNotNull(task.get("status"),
                "Task must have 'status' field (not 'taskStatus'). "
                + "If this assertion fails, the field name fix was not applied correctly.");
        assertEquals(AgentExecStatus.failed.name(), task.get("status"));
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
                .loadComponentModel("/nop/record/mapping/agent-plan.record-mappings.xml");
        return defs.getMapping("Markdown_to_AgentPlanModel");
    }
}
