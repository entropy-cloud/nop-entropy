package io.nop.treesitter.compat;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.RustScanner;

/**
 * Rust language adapter ({@code org.treesitter.TreeSitterRust} shape) over the
 * vendored pure-Java rust grammar blob (tree-sitter v0.25.8). The rust grammar's
 * raw-string/float/comment external tokens are hand-translated as
 * {@link RustScanner} (its C scanner is not part of the blob), so the language
 * wires its scanner factory here — consumers parse through the plain compat API
 * exactly like the JNI version.
 */
public class TreeSitterRust implements TreeSitterLanguage {

    private static final Language LANGUAGE =
            Language.fromClasspath("/grammars/rust/tree-sitter-rust-blob.bin");

    static {
        LANGUAGE.setExternalScannerFactory(RustScanner::new);
    }

    @Override
    public Language language() {
        return LANGUAGE;
    }
}
