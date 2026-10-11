package io.nop.treesitter.compat;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.PythonScanner;

/**
 * Python language adapter ({@code org.treesitter.TreeSitterPython} shape) over
 * the vendored pure-Java python grammar blob. The python grammar's indentation
 * scanner is the hand-written Java {@link PythonScanner} (its C scanner is not
 * part of the blob), so the language wires its scanner factory here —
 * consumers parse through the plain compat API exactly like the JNI version.
 */
public class TreeSitterPython implements TreeSitterLanguage {

    private static final Language LANGUAGE =
            Language.fromClasspath("/grammars/python/tree-sitter-python-blob.bin");

    static {
        LANGUAGE.setExternalScannerFactory(PythonScanner::new);
    }

    @Override
    public Language language() {
        return LANGUAGE;
    }
}
