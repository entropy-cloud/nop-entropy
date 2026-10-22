package io.nop.lint.java;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.java.semantic.JavaTypeResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The project mode of the L2 {@link JavaTypeResolver} (roadmap item 37/40
 * production wiring): {@code initProject} scans the root for source roots
 * and binds a {@code JavaParserTypeSolver} per root, so a receiver whose
 * declaration lives in ANOTHER file of the project resolves — the exact
 * face the v1 single-file + reflection model could not answer (it degraded
 * instead). Through the real engine, an L2 rule that needs the cross-file
 * type produces its diagnostic with zero degradation.
 */
public class TestJavaTypeResolverProjectMode {

    private static final String RULE_PATH = "/test/lint/java-l2/demo/l2-string-only.rule.yml";

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private Path write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * The cross-file face: {@code b.tag()} returns String — declared in
     * B.java, queried in C.java. The resolver answers through the project
     * source roots, and the L2 rule runs (never degrades).
     */
    @Test
    public void crossFileReceiverResolvesThroughTheProject() throws IOException {
        write("src/main/java/B.java",
                "public class B {\n    String tag() {\n        return \"t\";\n    }\n}\n");
        Path c = write("src/main/java/C.java",
                "public class C {\n    String m(B b) {\n        return b.tag().toString();\n    }\n}\n");

        JavaTypeResolver resolver = new JavaTypeResolver();
        resolver.initProject(project);

        // direct face: the receiver of toString() is the String from B.tag()
        assertTrue(resolver.isAssignableTo(c.toString(), 2, 15, "String"),
                "the cross-file receiver resolves through the project source roots");

        // engine face: the L2 rule runs and reports, zero degradation
        LintEngine engine = new LintEngine(LanguageRegistry.discoverDefaults(),
                LintProfile.STANDARD, resolver);
        LintResult result = engine.lint(
                List.of((RuleDslModel) new RuleDslParser().loadRuleModel(RULE_PATH)),
                LanguageRegistry.discoverDefaults().resolve("java"),
                c.toString(), Files.readString(c));
        assertEquals(1, result.diagnostics().size(),
                "the cross-file String receiver is reported by the L2 rule");
        assertEquals(0, result.stats().getRulesDegraded(),
                "project mode answers the query — no degrade");
    }

    @Test
    public void rootWithoutSourceRootsFailsClosed() throws IOException {
        write("README.md", "no java here");
        JavaTypeResolver resolver = new JavaTypeResolver();
        assertThrows(io.nop.lint.core.semantic.TypeResolutionException.class,
                () -> resolver.initProject(project),
                "a declared project without source roots must fail, never silently "
                        + "run reflection-only");
    }

    @Test
    public void nullRootResetsToReflectionOnly() throws IOException {
        write("src/main/java/B.java", "public class B {}\n");
        JavaTypeResolver resolver = new JavaTypeResolver();
        resolver.initProject(project);
        resolver.initProject(null);
        // no exception; the single-file model is active again
        assertTrue(resolver.isAvailable());
    }
}
