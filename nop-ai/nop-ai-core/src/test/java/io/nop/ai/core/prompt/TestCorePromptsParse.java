package io.nop.ai.core.prompt;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17/plan 2454: nop-ai-core now hosts the merged prompt resource tree
 * (coder + deepwiki + translate sources). Every .prompt.yaml under
 * /nop/ai/prompts must parse under the prompt xdef — a malformed file here
 * breaks downstream prompt gates (e.g. coder TestAiCoderHelper.parseAllPrompts)
 * at a distance. Fails with the offending path list.
 */
@NopTestConfig
public class TestCorePromptsParse extends JunitBaseTestCase {

    @Inject
    IPromptTemplateManager promptTemplateManager;

    @Test
    public void testAllPromptsUnderCoreVfsParse() {
        Collection<? extends IResource> resources =
                VirtualFileSystem.instance().getAllResources("/nop/ai/prompts", "prompt.yaml");
        List<String> failures = new ArrayList<>();
        int parsed = 0;
        for (IResource r : resources) {
            if (!r.getName().endsWith(".prompt.yaml")) {
                continue;
            }
            try {
                promptTemplateManager.loadPromptTemplateFromPath(r.getPath());
                parsed++;
            } catch (Exception e) {
                failures.add(r.getPath() + " => " + e);
            }
        }
        assertTrue(parsed > 0, "no .prompt.yaml resources found under /nop/ai/prompts");
        assertTrue(failures.isEmpty(), "malformed prompt resources must be fixed: " + failures);
    }
}
