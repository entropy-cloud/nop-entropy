package io.nop.treesitter.biz;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.provider.ITreeSitterLanguageProvider;

import java.util.Set;

/**
 * Test-only ServiceLoader registration: re-exposes the json blob under the
 * name "custom-json" to prove third-party extension of the default provider.
 */
public class TestCustomGrammarProvider implements ITreeSitterLanguageProvider {

    @Override
    public Language getLanguage(String name) {
        return "custom-json".equals(name) ? Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin") : null;
    }

    @Override
    public Set<String> languageNames() {
        return Set.of("custom-json");
    }
}
