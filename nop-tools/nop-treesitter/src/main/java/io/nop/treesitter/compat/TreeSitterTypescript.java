package io.nop.treesitter.compat;

import io.nop.treesitter.language.Language;

/**
 * TypeScript language adapter ({@code org.treesitter.TreeSitterTypescript}
 * shape); serves both .ts and .tsx sources through the typescript blob.
 */
public class TreeSitterTypescript implements TreeSitterLanguage {

    private static final Language LANGUAGE =
            Language.fromClasspath("/grammars/typescript/tree-sitter-typescript-blob.bin");

    @Override
    public Language language() {
        return LANGUAGE;
    }
}
