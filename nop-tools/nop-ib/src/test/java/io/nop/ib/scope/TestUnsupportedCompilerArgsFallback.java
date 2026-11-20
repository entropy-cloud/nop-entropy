package io.nop.ib.scope;

import io.nop.ib.compile.MavenFallback;
import io.nop.ib.model.EffectivePomParser;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-11-20 nop-rg-vector defect pin (plan 2574 follow-up): pom compilerArgs the
 * fast path cannot mirror ({@code --add-modules jdk.incubator.vector}) previously
 * went to the incremental javac unmirrored — the incubator package was invisible
 * and the module compiled red. Now: the parser flags such modules
 * ({@code unsupportedCompilerArgs}) and the planner routes any plan touching them
 * to the Maven fallback with a reason naming the module; mirrorable args
 * ({@code -parameters}, {@code -g}) and plain modules are unaffected.
 */
class TestUnsupportedCompilerArgsFallback {

    private static final String POM_WITH_ARGS = """
                <?xml version="1.0" encoding="UTF-8"?>
                <projects xmlns="http://maven.apache.org/POM/4.0.0">
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>g</groupId>
                    <artifactId>vector-mod</artifactId>
                    <version>1</version>
                    <packaging>jar</packaging>
                    <build>
                      <sourceDirectory>/REPO/vector-mod/src/main/java</sourceDirectory>
                      <testSourceDirectory>/REPO/vector-mod/src/test/java</testSourceDirectory>
                      <outputDirectory>/REPO/vector-mod/target/classes</outputDirectory>
                      <testOutputDirectory>/REPO/vector-mod/target/test-classes</testOutputDirectory>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <configuration>
                            <compilerArgs>
                              %ARGS%
                            </compilerArgs>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                </projects>
                """;

    private static final String ARGS_VECTOR = "<arg>--add-modules</arg>\n                              <arg>jdk.incubator.vector</arg>";

    private static String pomWith(String argLines) {
        return POM_WITH_ARGS.replace("%ARGS%", argLines);
    }

    private static final String POM_MIRRORABLE_ONLY = pomWith("<arg>-parameters</arg>");

    private Path repoRoot;

    private IbModule parse(String pomText) throws Exception {
        repoRoot = Files.createTempDirectory("ib-uca-repo");
        // fixture 的 /REPO/ 占位指向真实存在的目录结构（parser 解析相对化路径）
        String resolved = pomText.replace("/REPO/", repoRoot.toString() + "/");
        Files.createDirectories(repoRoot.resolve("vector-mod/src/main/java"));
        Files.createDirectories(repoRoot.resolve("vector-mod/src/test/java"));
        Path pom = repoRoot.resolve("vector-mod/pom.xml");
        Files.write(pom, resolved.getBytes(StandardCharsets.UTF_8));
        try {
            return new EffectivePomParser(repoRoot.toString() + "/").parse(pom.toFile()).get(0);
        } finally {
            Files.deleteIfExists(pom);
        }
    }

    private IbModule parseSilently(String pomText) {
        try {
            return parse(pomText);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private IbModel modelWith(IbModule mod) {
        IbModel model = new IbModel();
        model.setRepoRoot("/repo");
        model.addModule(mod);
        model.getModulesByArtifactId().put(mod.getArtifactId(), mod);
        return model;
    }

    /** pin 1 (2026-11-20 update): --add-modules is now MIRRORED, not flagged —
     *  the values are captured for the fast path to replay verbatim. */
    @Test
    void addModulesCompilerArgIsMirrored() throws Exception {
        IbModule mod = parse(pomWith(ARGS_VECTOR));
        assertFalse(mod.hasUnsupportedCompilerArgs(),
                "--add-modules is mirrorable now — must NOT flag the module");
        assertEquals(List.of("jdk.incubator.vector"), mod.getCompileAddModules(),
                "--add-modules values must be captured for the fast path");
        // '=' 单 token 形态同效
        IbModule mod2 = parse(pomWith("<arg>--add-modules=jdk.incubator.vector</arg>"));
        assertEquals(List.of("jdk.incubator.vector"), mod2.getCompileAddModules(),
                "--add-modules=x single-token form must parse");
    }

    /** pin 1b: unknown args still flag (fail-safe set is closed, not open). */
    @Test
    void unknownCompilerArgStillFlags() throws Exception {
        IbModule mod = parse(pomWith("<arg>-Xlint:all</arg>"));
        assertTrue(mod.hasUnsupportedCompilerArgs(),
                "non-mirrorable args must still route to Maven (fail-safe)");
    }

    /** pin 1c: --enable-preview is captured as a mirrorable flag. */
    @Test
    void enablePreviewCaptured() throws Exception {
        IbModule mod = parse(pomWith("<arg>--add-modules</arg>\n                              <arg>jdk.incubator.vector</arg>\n                              <arg>--enable-preview</arg>"));
        assertFalse(mod.hasUnsupportedCompilerArgs());
        assertTrue(mod.getCompileEnablePreview(), "--enable-preview must be captured");
        assertEquals(List.of("jdk.incubator.vector"), mod.getCompileAddModules());
    }

    /** pin 2: mirrorable args do NOT flag; the -parameters contract still works. */
    @Test
    void mirrorableArgsNotFlagged() throws Exception {
        IbModule mod = parse(POM_MIRRORABLE_ONLY);
        assertFalse(mod.hasUnsupportedCompilerArgs(),
                "-parameters is mirrored by the fast path, must not flag");
        Map<String, Object> m = mod.getCompilerExecs().get(0).toMap();
        assertTrue(IbModule.CompilerExec.fromMap(m).isParameters(),
                "-parameters detection (pre-existing contract) still works");
    }

    /** pin 3: a change inside a flagged module forces Maven fallback naming it. */
    @Test
    void flaggedModuleChangeForcesFallbackNamingIt() {
        IbModule mod = parseSilently(pomWith("<arg>-Xlint:all</arg>"));
        ScopePlanner planner = new ScopePlanner(modelWith(mod),
                new ModuleGraph(modelWith(mod)), id -> null);
        PlanResult plan = planner.plan(List.of("vector-mod/src/main/java/A.java"), null, true);
        assertTrue(plan.requiresMavenFallback(),
                "flagged module must not take the fast path (fail-safe)");
        String reason = plan.getFallbackReason();
        assertTrue(reason != null && reason.contains("vector-mod"),
                () -> "reason must name the module: " + reason);
        assertTrue(reason.contains("compilerArgs"), () -> "reason must say why: " + reason);
    }

    /** pin 4: a module without compilerArgs keeps the fast path. */
    @Test
    void plainModuleKeepsFastPath() {
        IbModule plain = new IbModule();
        plain.setGroupId("g");
        plain.setArtifactId("plain");
        plain.setVersion("1");
        plain.setBasedir("plain");
        plain.setSourceDir("plain/src/main/java");
        plain.setTestSourceDir("plain/src/test/java");
        ScopePlanner planner = new ScopePlanner(modelWith(plain),
                new ModuleGraph(modelWith(plain)), id -> null);
        PlanResult plan = planner.plan(List.of("plain/src/main/java/A.java"), null, true);
        assertFalse(plan.requiresMavenFallback(),
                "module without unsupported compilerArgs must keep the fast path");
    }

    /** pin 5: the fact survives compilerExec map round-trip (model persistence) —
     *  both the unsupported flag and the captured addModules values. */
    @Test
    void factSurvivesRoundTrip() throws Exception {
        IbModule mod = parse(pomWith(ARGS_VECTOR));
        Map<String, Object> m = mod.getCompilerExecs().get(0).toMap();
        IbModule.CompilerExec restored = IbModule.CompilerExec.fromMap(m);
        assertFalse(restored.isUnsupportedCompilerArgs());
        assertEquals(List.of("jdk.incubator.vector"), restored.getAddModules());
        // unknown-arg 形态的 flag 同样 round-trip
        IbModule mod2 = parse(pomWith("<arg>-Xlint:all</arg>"));
        assertTrue(IbModule.CompilerExec.fromMap(mod2.getCompilerExecs().get(0).toMap())
                .isUnsupportedCompilerArgs());
    }

    /** pin 7 (timeout governance): nop.ib.maven.timeout overrides the delegation
     *  window — default shrank 60min → 10min (lock-starved lane held 1h, E2E 2026-11-20). */
    @Test
    void mavenTimeoutPropertyOverridable() {
        String old = System.getProperty("nop.ib.maven.timeout");
        try {
            System.setProperty("nop.ib.maven.timeout", "3");
            assertEquals(java.time.Duration.ofMinutes(3),
                    new MavenFallback(java.nio.file.Path.of("/repo")).getTimeout());
        } finally {
            if (old == null)
                System.clearProperty("nop.ib.maven.timeout");
            else
                System.setProperty("nop.ib.maven.timeout", old);
        }
    }

    /** pin 6 (MavenFallback fix): -pl selectors use the :artifactId reactor form —
     *  bare artifactIds make Maven fail with "requested required projects ... do not
     *  exist" (exposed live by the nop-rg-vector fallback on 2026-11-20). */
    @Test
    void plSelectorsUseColonPrefix() {
        assertEquals(":nop-rg-vector", MavenFallback.plSelectors(List.of("nop-rg-vector")));
        assertEquals(":a,:b", MavenFallback.plSelectors(List.of("a", "b")));
        assertEquals(":a", MavenFallback.plSelectors(List.of(":a")));
        assertEquals("", MavenFallback.plSelectors(List.of()));
    }
}
