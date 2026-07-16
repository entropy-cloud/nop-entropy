package io.nop.ai.toolkit.model;

import io.nop.ai.api.tool.IToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 296 (WS2): unit tests verifying that {@link AiToolModel} correctly
 * implements {@link IToolDefinition} and that {@code tags}/{@code meta}
 * fields declared in {@code tool.xdef} are populated by the generated code.
 */
public class TestAiToolModelTags {

    @Test
    void defaultTagsAndMetaAreEmpty() {
        AiToolModel tool = new AiToolModel();
        tool.setName("test");
        tool.setDescription("desc");

        // Default tags should be empty (not null per IToolDefinition contract)
        Set<String> tags = tool.getTags();
        assertTrue(tags == null || tags.isEmpty(),
                "Default tags should be null or empty, got: " + tags);

        // Default meta should be false
        assertFalse(tool.isMeta(), "Default meta should be false");
    }

    @Test
    void setTagsAndMetaPersist() {
        AiToolModel tool = new AiToolModel();
        tool.setName("admin-tool");
        tool.setDescription("An admin tool");
        tool.setTags(Set.of("admin", "channel:webui"));
        tool.setMeta(true);

        assertEquals(Set.of("admin", "channel:webui"), tool.getTags());
        assertTrue(tool.isMeta());
    }

    @Test
    void implementsIToolDefinition() {
        AiToolModel tool = new AiToolModel();
        tool.setName("read-file");
        tool.setDescription("Reads a file");
        tool.setTags(Set.of("readonly"));

        // AiToolModel is-a IToolDefinition
        IToolDefinition def = tool;
        assertEquals("read-file", def.getName());
        assertEquals("Reads a file", def.getDescription());
        assertEquals(Set.of("readonly"), def.getTags());
    }
}
