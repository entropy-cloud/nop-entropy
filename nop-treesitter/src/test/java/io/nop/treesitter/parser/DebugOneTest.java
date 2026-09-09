package io.nop.treesitter.parser;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

class DebugOneTest {
    @Test
    void debugParse() {
        Language l = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        System.err.println("RESULT " + TSParser.parse(l, "[1 2,").toSexpString(false));
    }
}
