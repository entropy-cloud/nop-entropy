package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.biz.PanelComponentConfigArea;
import io.nop.datav.biz.PanelComponentMeta;
import io.nop.datav.service.component.IPanelComponent;
import io.nop.datav.service.component.PanelComponentRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * ChatBI 组件发现工具（D6-2）：将 {@link PanelComponentRegistry} 的 14 类组件 + 各自配置区域描述符 +
 * 是否需要数据集绑定暴露给 LLM，使其能选择合适组件类型创作大屏。
 *
 * <p>对应工具定义 {@code datav-list-component-types.tool.xml}。设计契约见
 * {@code ai-dev/design/nop-datav/ai-design.md} §9（裁定 O：独立 IToolExecutor，不经 BizModel 调用链）。</p>
 *
 * <p>直接读 {@link PanelComponentRegistry}（单例，代码构建自包含），不依赖 DB / IoC 注入。
 * 大屏可用全部 14 类（含装饰类型，与看板不同——看板只能用 8 类）。</p>
 */
public class DatavListComponentTypesExecutor implements IToolExecutor {

    public static final String TOOL_NAME = "datav-list-component-types";

    private final PanelComponentRegistry componentRegistry = PanelComponentRegistry.getInstance();

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        Collection<IPanelComponent> components = componentRegistry.getComponents().values();
        List<Map<String, Object>> componentList = new ArrayList<>(components.size());
        for (IPanelComponent component : components) {
            PanelComponentMeta meta = component.getMetadata();
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("type", meta.getType());
            descriptor.put("displayName", meta.getDisplayName());
            descriptor.put("needsDataset", meta.isNeedsDataset());
            descriptor.put("configAreas", configAreasToJson(meta.getConfigAreas()));
            componentList.add(descriptor);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("components", componentList);

        String json = JsonTool.stringify(result);
        return FutureHelper.success(AiToolCallResult.successResult(call.getId(), json));
    }

    private List<Map<String, Object>> configAreasToJson(List<PanelComponentConfigArea> areas) {
        if (areas == null || areas.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> list = new ArrayList<>(areas.size());
        for (PanelComponentConfigArea area : areas) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", area.getName());
            m.put("description", area.getDescription());
            m.put("required", area.isRequired());
            list.add(m);
        }
        return list;
    }
}
