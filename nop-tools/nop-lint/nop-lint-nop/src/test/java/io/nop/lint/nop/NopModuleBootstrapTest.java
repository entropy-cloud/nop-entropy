package io.nop.lint.nop;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.provider.DefaultTreeSitterLanguageProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module bootstrap proof: the language provider nop-platform rules will rely
 * on is instantiable here and serves every built-in grammar by name.
 * Contains-based (not count-based) so third-party providers discovered on a
 * future classpath cannot break the assertion.
 */
class NopModuleBootstrapTest {

    @Test
    void providerServesAllBuiltInLanguages() {
        DefaultTreeSitterLanguageProvider provider = new DefaultTreeSitterLanguageProvider();

        Language java = provider.getLanguage("java");
        assertNotNull(java, "provider must serve the java grammar");

        for (String name : new String[]{"json", "java", "javascript", "python", "typescript", "tsx"}) {
            assertTrue(provider.languageNames().contains(name),
                    "built-in language " + name + " must be advertised");
        }
    }
}
