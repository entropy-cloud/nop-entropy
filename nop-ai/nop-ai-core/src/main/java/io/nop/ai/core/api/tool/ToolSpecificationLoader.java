package io.nop.ai.core.api.tool;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;

/**
 * @deprecated Use {@link io.nop.ai.toolkit.api.IToolManager#loadTool(String)} instead (loads {@link io.nop.ai.toolkit.model.AiToolModel} from {@code /nop/ai/tools/{name}.tool.xml}).
 */
@Deprecated
public class ToolSpecificationLoader {
    public static ToolSpecification loadSpecification(String toolName) {
        String path = "/nop/ai/tools/" + toolName + ".tool.json";
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists())
            return null;

        return JsonTool.loadDeltaBeanFromResource(resource, ToolSpecification.class, null);
    }
}
