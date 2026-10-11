package io.nop.lint.js;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring proof for the ServiceLoader registration shipped in this module
 * ({@code META-INF/services/io.nop.lint.core.lang.LintLanguage}): the
 * loader must discover real, parseable {@link TypeScriptLanguage} and
 * {@link TsxLanguage} bindings — not merely find the registration file.
 * Classpath discovery instantiates through the public no-arg constructors;
 * the shared-adapter contract guarantees the discovered instances are
 * functionally identical to the {@code get()} singletons (one grammar decode
 * per JVM).
 */
public class JsBindingDiscoveryTest {

    private static List<LintLanguage> discover() {
        List<LintLanguage> discovered = new ArrayList<>();
        for (LintLanguage language : ServiceLoaderShim.load()) {
            discovered.add(language);
        }
        return discovered;
    }

    @Test
    void serviceLoaderDiscoversBothBindings() {
        List<LintLanguage> discovered = discover();
        assertTrue(discovered.stream().anyMatch(l -> l instanceof TypeScriptLanguage),
                "the TypeScript binding must be registered");
        assertTrue(discovered.stream().anyMatch(l -> l instanceof TsxLanguage),
                "the TSX binding must be registered");
    }

    @Test
    void discoveredIdsMatchRegistryContract() {
        for (LintLanguage language : discover()) {
            if (language instanceof TypeScriptLanguage) {
                assertEquals("typescript", language.id(),
                        "registration and binding id must agree (lowercase contract)");
            }
            if (language instanceof TsxLanguage) {
                assertEquals("tsx", language.id(),
                        "registration and binding id must agree (lowercase contract)");
            }
            // the parseability assertion covers this module's own bindings;
            // other bindings discovered from dependency jars (e.g. the XML
            // language) parse their own syntax, not TypeScript
            if (language instanceof TypeScriptLanguage || language instanceof TsxLanguage) {
                assertNotNull(language.parse("const ok = 1;").root(),
                        "the discovered binding must be parseable");
            }
        }
    }

    @Test
    void discoveredInstancesShareTheSingletonAdapters() {
        for (LintLanguage language : discover()) {
            if (language instanceof TypeScriptLanguage) {
                Language backend = language.treeSitter();
                assertNotNull(backend);
                assertSame(TypeScriptLanguage.get().treeSitter(), backend,
                        "discovered instances must share the one adapter (no re-decode)");
            }
            if (language instanceof TsxLanguage) {
                Language backend = language.treeSitter();
                assertNotNull(backend);
                assertSame(TsxLanguage.get().treeSitter(), backend,
                        "discovered instances must share the one adapter (no re-decode)");
            }
        }
    }

    /**
     * java.util.ServiceLoader referenced through a named holder so the test
     * reads like the discovery path it proves.
     */
    private static final class ServiceLoaderShim {
        static Iterable<LintLanguage> load() {
            return java.util.ServiceLoader.load(LintLanguage.class);
        }
    }
}
