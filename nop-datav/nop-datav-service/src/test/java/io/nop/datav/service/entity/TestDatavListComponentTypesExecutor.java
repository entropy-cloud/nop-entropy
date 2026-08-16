package io.nop.datav.service.entity;

import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.service.chatbi.DatavListComponentTypesExecutor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatavListComponentTypesExecutor} 单元测试（D6-2 Phase 2 Exit Criteria）。
 *
 * <p>覆盖：返回 14 类组件（数量 + 代表类型 chart/stat-tile/decorative-border）+ needsDataset 标志正确 +
 * 接线验证（IToolManager.listTools 包含 datav-list-component-types 且可调用）。</p>
 */
public class TestDatavListComponentTypesExecutor extends AbstractNopDatavTest {

    @Test
    public void testListComponentTypesReturnsAll14() {
        DatavListComponentTypesExecutor exec = new DatavListComponentTypesExecutor();
        AiToolCall call = new AiToolCall();
        call.setToolName(DatavListComponentTypesExecutor.TOOL_NAME);
        call.setInput("{}");

        AiToolCallResult result = exec.executeAsync(call, null).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) parsed.get("components");

        // 14 类组件（8 通用 + 6 装饰/媒体）
        assertEquals(14, components.size(), "must return all 14 component types");

        // 代表类型存在
        assertTrue(containsType(components, "chart"), "chart must be listed");
        assertTrue(containsType(components, "stat-tile"), "stat-tile must be listed");
        assertTrue(containsType(components, "decorative-border"), "decorative-border must be listed (大屏可用全部 14 类)");
        assertTrue(containsType(components, "table"), "table must be listed");
        assertTrue(containsType(components, "pivot-table"), "pivot-table must be listed");
        assertTrue(containsType(components, "map"), "map must be listed");
        assertTrue(containsType(components, "text"), "text must be listed");
        assertTrue(containsType(components, "video"), "video must be listed");
        assertTrue(containsType(components, "stream"), "stream must be listed");
        assertTrue(containsType(components, "carousel-tab"), "carousel-tab must be listed");

        // needsDataset 标志正确
        assertTrue(findComponent(components, "chart").get("needsDataset").equals(Boolean.TRUE),
                "chart needsDataset=true");
        assertTrue(findComponent(components, "stat-tile").get("needsDataset").equals(Boolean.TRUE),
                "stat-tile needsDataset=true");
        assertTrue(findComponent(components, "table").get("needsDataset").equals(Boolean.TRUE),
                "table needsDataset=true");
        assertFalse(findComponent(components, "text").get("needsDataset").equals(Boolean.TRUE),
                "text needsDataset=false");
        assertFalse(findComponent(components, "decorative-border").get("needsDataset").equals(Boolean.TRUE),
                "decorative-border needsDataset=false");
    }

    @Test
    public void testConfigAreasPresentForDecorativeComponents() {
        DatavListComponentTypesExecutor exec = new DatavListComponentTypesExecutor();
        AiToolCall call = new AiToolCall();
        call.setToolName(DatavListComponentTypesExecutor.TOOL_NAME);
        call.setInput("{}");

        AiToolCallResult result = exec.executeAsync(call, null).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) parsed.get("components");

        // decorative-border 声明了 configAreas（variant 必填 + color 可选）
        Map<String, Object> border = findComponent(components, "decorative-border");
        assertNotNull(border.get("configAreas"), "decorative-border must have configAreas");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> areas = (List<Map<String, Object>>) border.get("configAreas");
        assertFalse(areas.isEmpty(), "decorative-border configAreas non-empty");
        assertTrue(areas.stream().anyMatch(a -> "variant".equals(a.get("name"))),
                "decorative-border has 'variant' config area");
    }

    @Test
    public void testWiredAndCallableViaToolManager() {
        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        provider.setExecutors(Collections.singletonList(new DatavListComponentTypesExecutor()));
        IToolManager toolManager = new ToolManagerImpl(provider, Collections.emptyList());

        // IToolManager.listTools 包含 datav-list-component-types（接线 rule #23）
        boolean listed = toolManager.listTools().stream()
                .anyMatch(t -> DatavListComponentTypesExecutor.TOOL_NAME.equals(t.getName()));
        assertTrue(listed, "IToolManager.listTools() must include datav-list-component-types");

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavListComponentTypesExecutor.TOOL_NAME);
        call.setInput("{}");

        AiToolCallResult result = toolManager.callTool(
                DatavListComponentTypesExecutor.TOOL_NAME, call, null).join();

        assertEquals("success", result.getStatus(), "wired executor must return success via IToolManager");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        assertNotNull(parsed.get("components"), "result contains components array (Anti-Hollow)");
    }

    @SuppressWarnings("unchecked")
    private static boolean containsType(List<Map<String, Object>> components, String type) {
        return components.stream().anyMatch(c -> type.equals(c.get("type")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findComponent(List<Map<String, Object>> components, String type) {
        return components.stream().filter(c -> type.equals(c.get("type"))).findFirst().orElse(null);
    }
}
