package io.nop.treesitter.compat;

import io.nop.treesitter.language.Language;

/**
 * Go language adapter ({@code org.treesitter.TreeSitterGo} shape) over the
 * vendored pure-Java go grammar blob (tree-sitter v0.25.8). The go grammar
 * ships reserved-word sets but no external scanner, so no scanner factory
 * is wired.
 */
public class TreeSitterGo implements TreeSitterLanguage {

    private static final Language LANGUAGE =
            Language.fromClasspath("/grammars/go/tree-sitter-go-blob.bin");

    @Override
    public Language language() {
        return LANGUAGE;
    }
}
