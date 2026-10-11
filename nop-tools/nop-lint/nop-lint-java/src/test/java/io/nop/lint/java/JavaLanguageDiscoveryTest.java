package io.nop.lint.java;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring proof for the ServiceLoader registration shipped in this module
 * ({@code META-INF/services/io.nop.lint.core.lang.LintLanguage}): the
 * loader must discover a real, parseable {@link JavaLanguage} — not merely
 * find the registration file. Classpath discovery instantiates through the
 * public no-arg constructor; the shared-adapter contract guarantees the
 * discovered instance is functionally identical to {@link JavaLanguage#get()}
 * (one grammar decode per JVM).
 */
public class JavaLanguageDiscoveryTest {

    private static List<JavaLanguage> discover() {
        List<JavaLanguage> discovered = new ArrayList<>();
        for (LintLanguage language : ServiceLoader.load(LintLanguage.class)) {
            if (language instanceof JavaLanguage java) {
                discovered.add(java);
            }
        }
        return discovered;
    }

    @Test
    void serviceLoaderDiscoversJavaLanguage() {
        List<JavaLanguage> discovered = discover();

        assertEquals(1, discovered.size(), "exactly one Java binding must be registered");
        assertTrue(discovered.get(0).parse("class A {}").root() != null,
                "the discovered binding must be parseable");
    }

    @Test
    void discoveredIdMatchesRegistryContract() {
        for (JavaLanguage language : discover()) {
            assertEquals("java", language.id(),
                    "registration and binding id must agree (lowercase contract)");
            Language backend = language.treeSitter();
            assertNotNull(backend);
            assertSame(JavaLanguage.get().treeSitter(), backend,
                    "discovered instances must share the one adapter (no re-decode)");
        }
    }
}
