package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.ResourceLoadingCache;

public class DefaultSystemPromptLoader {
    public static DefaultSystemPromptLoader _INSTANCE = new DefaultSystemPromptLoader();

    private final ResourceLoadingCache<String> promptCache = new ResourceLoadingCache<>("system-prompt-cache",
            this::loadPrompt, null);

    public static DefaultSystemPromptLoader instance() {
        return _INSTANCE;
    }

    public String loadSystemPrompt(AiChatOptions chatOptions) {
        String workMode = chatOptions.getWorkMode();
        if (StringHelper.isEmpty(workMode))
            return "";

        StringBuilder sb = new StringBuilder();
        if (isEnabled(chatOptions.getEnableCognitivePrompt())) {
            sb.append("[COGNITIVE ARCHITECTURE - " + workMode.toUpperCase() + "]\n");
            String path = buildPromptPath(workMode, "cognitive");
            sb.append(promptCache.get(path));
            sb.append("\n\n");
        }

        if (isEnabled(chatOptions.getEnableMetaPrompt())) {
            sb.append("[META-PROMPT - " + workMode.toUpperCase() + "]\n");
            String path = buildPromptPath(workMode, "meta");
            sb.append(promptCache.get(path));
            sb.append("\n\n");
        }

        if (isEnabled(chatOptions.getEnableSystemPrompt())) {
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