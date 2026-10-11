package io.nop.treesitter.compat;

import io.nop.treesitter.language.Language;

/**
 * Migration bridge for the JNI-embedded tree-sitter API (io.github.bonede):
 * a language adapter yielding this module's {@link Language}. Implementations
 * mirror the {@code org.treesitter.TreeSitterXxx} grammar classes so consumer
 * code migrates by changing imports only.
 */
public interface TreeSitterLanguage {

    /**
     * The runtime language backing this adapter.
     */
    Language language();
}
