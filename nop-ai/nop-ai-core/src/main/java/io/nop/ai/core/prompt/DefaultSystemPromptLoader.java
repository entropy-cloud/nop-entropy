package io.nop.ai.core.prompt;

import io.nop.ai.api.secure.SecureDefault;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.ResourceLoadingCache;

@SecureDefault
public class DefaultSystemPromptLoader {
    // 3.1-MA-12（plan 2555 裁定 B / C1-003）：public 换装面 volatile 化——换装可见 + 安全发布
    public static volatile DefaultSystemPromptLoader _instance = new DefaultSystemPromptLoader();

    private final ResourceLoadingCache<String> promptCache = new ResourceLoadingCache<>("system-prompt-cache",
            this::loadPrompt, null);

    public static DefaultSystemPromptLoader instance() {
        return _instance;
    }

    public String loadSystemPrompt(String workMode, Boolean enableCognitivePrompt, Boolean enableMetaPrompt,
                                   Boolean enableSystemPrompt) {
        if (StringHelper.isEmpty(workMode))
            return "";

        StringBuilder sb = new StringBuilder();
        if (isEnabled(enableCognitivePrompt)) {
            sb.append("[COGNITIVE ARCHITECTURE - " + workMode.toUpperCase() + "]\n");
            String path = buildPromptPath(workMode, "cognitive");
            sb.append(promptCache.get(path));
            sb.append("\n\n");
        }

        if (isEnabled(enableMetaPrompt)) {
            sb.append("[META-PROMPT - " + workMode.toUpperCase() + "]\n");
            String path = buildPromptPath(workMode, "meta");
            sb.append(promptCache.get(path));
            sb.append("\n\n");
        }

        if (isEnabled(enableSystemPrompt)) {
            sb.append("[SYSTEM PROMPT - " + workMode.toUpperCase() + "]\n");
            String path = buildPromptPath(workMode, "system");
            sb.append(promptCache.get(path));
            sb.append("\n\n");
        }

        sb.append("I understand these " + workMode + " principles and I'm ready to help.");
        return sb.toString();
    }

    protected boolean isEnabled(Boolean b) {
        return b == null || b.booleanValue();
    }

    protected String buildPromptPath(String workMode, String fileName) {
        return "/nop/ai/prompts/system/" + workMode + "/" + fileName + ".md";
    }

    protected String loadPrompt(String path) {
        return ResourceHelper.readText(VirtualFileSystem.instance().getResource(path), null);
    }
}