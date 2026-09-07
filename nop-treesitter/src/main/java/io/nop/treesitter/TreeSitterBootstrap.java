package io.nop.treesitter;

import java.util.List;

/**
 * Placeholder entry point. Will be removed once the parser skeleton lands.
 *
 * <p>This file exists only so that the module compiles before the first plan
 * adds the real runtime types. It is intentionally a no-op class with a single
 * log-line method so that smoke tests can verify the module is on the
 * classpath without depending on any unstable API surface.</p>
 *
 * <p>The roadmap for nop-treesitter is at
 * {@code ai-dev/backlog/nop-treesitter-roadmap.md} and the driving mission is
 * {@code missions/nop-treesitter.json}.</p>
 */
public final class TreeSitterBootstrap {

    private TreeSitterBootstrap() {
    }

    public static String describe() {
        return "nop-treesitter (pure Java runtime, in development — see "
                + "ai-dev/backlog/nop-treesitter-roadmap.md)";
    }

    public static List<String> plannedModules() {
        return List.of(
                "io.nop.treesitter",
                "io.nop.treesitter.lexer",
                "io.nop.treesitter.parser",
                "io.nop.treesitter.subtree",
                "io.nop.treesitter.query",
                "io.nop.treesitter.scanner",
                "io.nop.treesitter.language",
                "io.nop.treesitter.cursor",
                "io.nop.treesitter.util"
        );
    }
}
