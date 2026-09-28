package io.nop.treesitter.compat;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.CSharpScanner;

/**
 * C# language adapter ({@code org.treesitter.TreeSitterCSharp} shape) over the
 * vendored pure-Java c-sharp grammar blob (tree-sitter v0.25.8). The grammar's
 * interpolation/raw-string/lambda external tokens are hand-translated as
 * {@link CSharpScanner} (its C scanner is not part of the blob), so the
 * language wires its scanner factory here.
 */
public class TreeSitterCSharp implements TreeSitterLanguage {

    private static final Language LANGUAGE =
            Language.fromClasspath("/grammars/c-sharp/tree-sitter-c-sharp-blob.bin");

    static {
        LANGUAGE.setExternalScannerFactory(CSharpScanner::new);
    }

    @Override
    public Language language() {
        return LANGUAGE;
    }
}
