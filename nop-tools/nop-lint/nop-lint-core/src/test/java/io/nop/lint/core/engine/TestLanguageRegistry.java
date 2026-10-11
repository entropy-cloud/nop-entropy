package io.nop.lint.core.engine;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.StubTestLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LanguageRegistry} proofs: manual registration, id normalization
 * (trim + case-insensitive — fixtures write {@code Java}, binding ids are
 * lowercase), conflict fail-closed, ServiceLoader discovery, and the
 * unknown/blank id error paths (no silent miss).
 */
public class TestLanguageRegistry {

    private static LintLanguage stubBinding(String id) {
        return new TreeSitterLanguageAdapter(id,
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    @Test
    public void manualRegisterAndResolve() {
        LanguageRegistry registry = LanguageRegistry.empty();
        LintLanguage binding = stubBinding("demo-lang");
        registry.register(binding);

        assertSame(binding, registry.resolve("demo-lang"));
        assertEquals(List.of("demo-lang"), registry.registeredIds());
    }

    @Test
    public void resolveIsCaseInsensitiveAndTrims() {
        LanguageRegistry registry = LanguageRegistry.empty();
        LintLanguage binding = stubBinding("java");
        registry.register(binding);

        assertSame(binding, registry.resolve("java"));
        assertSame(binding, registry.resolve("Java"), "fixture case must resolve to the lowercase id");
        assertSame(binding, registry.resolve(" JAVA "), "surrounding blanks must not break resolution");
    }

    @Test
    public void unknownIdFailsClosedWithOriginalValue() {
        LanguageRegistry registry = LanguageRegistry.empty();
        registry.register(stubBinding("java"));

        NopLintException ex = assertThrows(NopLintException.class, () -> registry.resolve("kotlin"));
        assertTrue(ex.getMessage().contains("kotlin"), "message must carry the original id: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("java"), "message must list registered ids: "
                + ex.getMessage());
    }

    @Test
    public void blankIdFailsClosed() {
        LanguageRegistry registry = LanguageRegistry.empty();

        assertThrows(NopLintException.class, () -> registry.resolve(null));
        assertThrows(NopLintException.class, () -> registry.resolve("   "));
        assertThrows(NopLintException.class, () -> registry.register(stubBinding("  ")));
    }

    @Test
    public void conflictingReRegistrationFailsClosed() {
        LanguageRegistry registry = LanguageRegistry.empty();
        LintLanguage first = stubBinding("java");
        LintLanguage second = stubBinding("java");
        assertNotSame(first, second);

        registry.register(first);
        NopLintException ex = assertThrows(NopLintException.class, () -> registry.register(second));
        assertTrue(ex.getMessage().contains("java"), "message must carry the id: " + ex.getMessage());
        assertSame(first, registry.resolve("java"), "the first binding must stay registered");
    }

    @Test
    public void reRegisteringSameInstanceIsIdempotent() {
        LanguageRegistry registry = LanguageRegistry.empty();
        LintLanguage binding = stubBinding("java");
        registry.register(binding);
        registry.register(binding);

        assertSame(binding, registry.resolve("java"));
        assertEquals(List.of("java"), registry.registeredIds(), "no duplicate id entry");
    }

    @Test
    public void serviceLoaderDiscoversTestProvider() {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();

        assertTrue(registry.registeredIds().contains("stub"),
                "the test-only provider must be discovered: " + registry.registeredIds());
        assertSame(StubTestLanguage.class, registry.resolve("STUB").getClass(),
                "discovery must yield the provider instance under normalized id lookup");
    }

    @Test
    public void nullBindingFailsClosed() {
        LanguageRegistry registry = LanguageRegistry.empty();
        assertThrows(NopLintException.class, () -> registry.register(null));
    }
}
