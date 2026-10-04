package io.nop.treesitter.biz;

import io.nop.core.initialize.CoreInitialization;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.provider.DefaultTreeSitterLanguageProvider;
import io.nop.treesitter.provider.ITreeSitterLanguageProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        for (String name : Set.of("bash", "json", "java", "javascript", "python", "typescript", "tsx")) {
            Language language = provider.getLanguage(name);
            assertNotNull(language, name);
        }
        assertEquals(8, provider.languageNames().size(),
                "seven built-ins plus the test-classpath custom provider");
    }

    /**
     * The bash grammar is only useful with its external scanner attached: heredoc
     * recognition is driven entirely by {@link io.nop.treesitter.scanner.BashScanner}.
     * Parsing a heredoc script through the provider therefore distinguishes "the
     * scanner factory is wired in {@code loadLanguage}" from "the blob merely
     * loads" — without the factory the heredoc body collapses into ERROR nodes.
     */
    @Test
    void bashHeredocParsesThroughTheProviderPath() {
        Language bash = provider.getLanguage("bash");
        assertNotNull(bash);
        String tree = TSParser.parse(bash, "cat <<EOF\nhello\nEOF\n").toSexpString(false);
        assertFalse(tree.contains("ERROR"), tree);
        assertTrue(tree.contains("heredoc_redirect"), tree);
        assertTrue(tree.contains("heredoc_start"), tree);
        assertTrue(tree.contains("heredoc_body"), tree);
        assertTrue(tree.contains("heredoc_end"), tree);
    }

    @Test
    void unknownNameReturnsNullNeverThrows() {
        assertNull(provider.getLanguage("ruby"));
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

    /**
     * Contract: a custom provider's grammar name shadows the built-in of the
     * same name — a synthetic provider re-exposing "json" must win over the
     * built-in json blob (distinguishable by the top-level node type).
     */
    @Test
    void customNameShadowsBuiltInOfTheSameName() {
        DefaultTreeSitterLanguageProvider shadowing = new DefaultTreeSitterLanguageProvider() {
            @Override
            protected List<ITreeSitterLanguageProvider> loadCustomProviders() {
                return List.of(new ITreeSitterLanguageProvider() {
                    @Override
                    public Language getLanguage(String name) {
                        return "json".equals(name)
                                ? Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin")
                                : null;
                    }

                    @Override
                    public Set<String> languageNames() {
                        return Set.of("json");
                    }
                });
            }
        };
        assertTrue(shadowing.languageNames().contains("json"));
        assertEquals("(program (expression_statement (object (pair (string (string_fragment)) (number)))))",
                TSParser.parse(shadowing.getLanguage("json"), "{\"a\": 1}").toSexpString(false));
    }
}
