package io.nop.treesitter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterBootstrapTest {

    @Test
    void describeReturnsRoadmapReference() {
        String desc = TreeSitterBootstrap.describe();
        assertTrue(desc.contains("nop-treesitter"), desc);
        assertTrue(desc.contains("ai-dev/backlog/nop-treesitter-roadmap.md"), desc);
    }

    @Test
    void plannedModulesIncludeAllSubpackages() {
        List<String> modules = TreeSitterBootstrap.plannedModules();
        assertEquals(9, modules.size());
        assertTrue(modules.contains("io.nop.treesitter.parser"));
        assertTrue(modules.contains("io.nop.treesitter.subtree"));
    }
}
