package io.nop.refactor.graphql;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.refactor.core.cli.NopRefactorCli;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI8 CLI same-shape proof (plan nop-refactor/08, roadmap WI8): the p0
 * demo set drives {@link NopRefactorCli#runFull} — the same engine's batch
 * face, zero second semantics. Preview exits 0 with the three-rewrite diff
 * and writes nothing; apply over the sample + exempted legacy pair exits 1
 * (one {@code OUT_OF_SCOPE} nonApply) with the byte-identical after source
 * landed; the {@code --json} face carries the same payload fields.
 */
class TestRefactorP0CliSameShape {

    private static final String P0_PREFIX = "/test/lint/refactor-p0";
    private static final Path FIXTURES = Path.of(
            "src/test/resources/_vfs/test/lint/refactor-p0/fixtures");

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private record Run(int exitCode, String stdout, String stderr) {
    }

    private Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = NopRefactorCli.runFull(args, LanguageRegistry.discoverDefaults(),
                LintProfile.STANDARD, new PrintStream(out), new PrintStream(err));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
    }

    private Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "refactor-p0-cli", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void previewExitsZeroShowsAllThreeRewritesAndWritesNothing() throws Exception {
        Path sample = writeTarget("p0/Sample.java", fixture("sample.before.java"));

        Run run = run("preview", "--rules", P0_PREFIX, sample.toString());

        assertEquals(0, run.exitCode(), run.stdout() + run.stderr());
        assertTrue(run.stdout().contains("log.info"), run.stdout());
        assertTrue(run.stdout().contains("Integer.valueOf"), run.stdout());
        assertTrue(run.stdout().contains("literal"), run.stdout());
        assertEquals(fixture("sample.before.java"), Files.readString(sample),
                "CLI preview writes nothing");
    }

    @Test
    void applyWithExemptionExitsOneLandsAfterAndReportsOutOfScope() throws Exception {
        Path sample = writeTarget("p0/Sample.java", fixture("sample.before.java"));
        Path legacy = writeTarget("p0/legacy/Legacy.java", fixture("legacy.before.java"));

        Run run = run("apply", "--rules", P0_PREFIX, sample.toString(), legacy.toString());

        assertEquals(1, run.exitCode(),
                "one exempted rewrite is a partial apply: " + run.stdout() + run.stderr());
        assertTrue(run.stdout().contains("[OUT_OF_SCOPE]"), run.stdout());
        assertEquals(fixture("sample.after.java"), Files.readString(sample),
                "apply landed the byte-identical after source");
        assertEquals(fixture("legacy.before.java"), Files.readString(legacy),
                "the exempted legacy file stays untouched");
    }

    @Test
    void jsonFaceCarriesTheSamePayloadFields() throws Exception {
        Path sample = writeTarget("p0/Sample.java", fixture("sample.before.java"));
        Path legacy = writeTarget("p0/legacy/Legacy.java", fixture("legacy.before.java"));

        Run console = run("preview", "--rules", P0_PREFIX, sample.toString(), legacy.toString());
        Run json = run("preview", "--json", "--rules", P0_PREFIX,
                sample.toString(), legacy.toString());

        assertEquals(console.exitCode(), json.exitCode());
        assertTrue(json.stdout().contains("\"applied\": false"), json.stdout());
        assertTrue(json.stdout().contains("\"diff\""), json.stdout());
        assertTrue(json.stdout().contains("\"nonApplied\""), json.stdout());
        assertTrue(json.stdout().contains("\"verification\""), json.stdout());
        assertTrue(json.stdout().contains("\"stats\""), json.stdout());
        assertTrue(json.stdout().contains("OUT_OF_SCOPE"), json.stdout());
    }
}
