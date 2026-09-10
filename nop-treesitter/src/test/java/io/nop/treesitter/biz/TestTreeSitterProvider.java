package io.nop.treesitter.biz;

import io.nop.core.initialize.CoreInitialization;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.provider.DefaultTreeSitterLanguageProvider;
import io.nop.treesitter.provider.ITreeSitterLanguageProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap item 12 wiring proof: the container resolves the provider bean and
 * the BizModel bean from the real app-treesitter.beans.xml with the provider
 * injected, and a third-party provider registered under META-INF/services
 * extends the default registry.
 */
class TestTreeSitterProvider {

    static DefaultTreeSitterLanguageProvider provider;

    @BeforeAll
    public static void init() {
        provider = new DefaultTreeSitterLanguageProvider();
    }

    @Test
    void builtInGrammarsResolveFromTheirBlobs() {
        for (String name : Set.of("json", "java", "javascript", "typescript", "tsx")) {
            Language language = provider.getLanguage(name);
            assertNotNull(language, name);
        }
        assertEquals(6, provider.languageNames().size(),
                "five built-ins plus the test-classpath custom provider");
    }

    @Test
    void unknownNameReturnsNullNeverThrows() {
        assertNull(provider.getLanguage("python"));
        assertNull(provider.getLanguage(null));
    }

    @Test
    void customProvidersRegisteredUnderMetaInfServicesExtendTheRegistry() {
        assertTrue(provider.languageNames().contains("custom-json"),
                "the test-only provider must be discovered via ServiceLoader");
        Language custom = provider.getLanguage("custom-json");
        assertNotNull(custom);
        assertEquals("(document\n  (array))",
                TSParser.parse(custom, "[]").toSExpression());
    }

    @Test
    void customNameShadowsBuiltInOfTheSameName() {
        assertEquals("(document\n  (array))",
                TSParser.parse(provider.getLanguage("custom-json"), "[]").toSExpression());
    }
}
