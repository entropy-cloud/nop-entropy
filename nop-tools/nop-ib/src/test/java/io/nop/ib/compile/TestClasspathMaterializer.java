package io.nop.ib.compile;

import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestClasspathMaterializer {

    @Test
    void testExternalJarResolvesCommonRepoFirstThenWorktreeFallback(@TempDir Path tmp) throws Exception {
        Path common = tmp.resolve("common-repo");
        Path worktree = tmp.resolve(".wt-repo");
        String rel = "com/example/lib/1.0/lib-1.0.jar";
        Files.createDirectories(common.resolve(rel).getParent());
        Files.writeString(common.resolve(rel), "common");
        IbModel model = new IbModel();
        model.setLocalRepo(worktree.toString());

        ClasspathMaterializer m = new ClasspathMaterializer(model);
        // NOP_IB_COMMON_REPO override points at the temp common repo
        ProcessBuilder pb = new ProcessBuilder("true");
        pb.environment().put("NOP_IB_COMMON_REPO", common.toString());
        // env can't be set for the current process portably; invoke via reflection-free
        // path: temporarily rely on the default resolution with user.home override
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", tmp.toString());
        try {
            Path jar = m.externalJar("com.example", "lib", "1.0", null);
            // ~/.m2/repository under overridden user.home does not contain the artifact
            // -> falls back to the worktree repo path (even though the file is absent)
            assertEquals(worktree.resolve(rel), jar);
            // now seed the "common" repo at user.home/.m2/repository and require it wins
            Path homeCommon = tmp.resolve(".m2/repository").resolve(rel);
            Files.createDirectories(homeCommon.getParent());
            Files.writeString(homeCommon, "common");
            jar = m.externalJar("com.example", "lib", "1.0", null);
            assertEquals(homeCommon, jar);
            assertTrue(Files.isRegularFile(jar));
        } finally {
            System.setProperty("user.home", oldHome);
        }
    }

    @Test
    void testClassifierShape(@TempDir Path tmp) {
        IbModel model = new IbModel();
        model.setLocalRepo(tmp.toString());
        ClasspathMaterializer m = new ClasspathMaterializer(model);
        Path jar = m.externalJar("g", "a", "1.0", "tests");
        assertEquals(tmp.resolve("g/a/1.0/a-1.0-tests.jar"), jar);
    }

    /**
     * Regression (flink observation, defect 16): an intra-project test-jar typed
     * dependency must resolve to the PACKAGED tests artifact from the local repo
     * (Maven surefire semantics). Materializing the sibling's target/test-classes
     * instead silently drops every harness class when the sibling's tests were
     * never compiled (e.g. project installed with -Dmaven.test.skip=true) and
     * test discovery dies with NoClassDefFoundError. The dir stays as fallback
     * for the dev case where no tests jar is installed.
     */
    @Test
    void testIntraTestJarPrefersPackagedTestsArtifactThenFallsBackToDir(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Path root = tmp.resolve("root");
        Files.createDirectories(repo.resolve("g/a/1.0"));
        Files.createDirectories(root.resolve("b/target/test-classes"));

        IbModule dep = new IbModule();
        dep.setGroupId("g");
        dep.setArtifactId("a");
        dep.setOutputDir("a/target/classes");
        dep.setTestOutputDir("a/target/test-classes");

        IbModel model = new IbModel();
        model.setRepoRoot(root.toString());
        model.setLocalRepo(repo.toString());
        model.addModule(dep);

        IbModule main = new IbModule();
        main.setGroupId("g");
        main.setArtifactId("main");
        main.setOutputDir("main/target/classes");
        main.setTestOutputDir("main/target/test-classes");
        main.setExternalArtifacts(List.of(
                "g:a:jar:1.0:compile",
                "g:a:test-jar:tests:1.0:test"));

        ClasspathMaterializer m = new ClasspathMaterializer(model);
        // no tests jar installed -> fallback to sibling test-classes dir
        var cp = m.testClasspath(main).entries();
        assertTrue(cp.contains("a/target/test-classes"),
                "fallback: sibling test-classes dir expected, got " + cp);
        assertTrue(cp.contains("a/target/classes"),
                "plain compile-scope dep still contributes main output, got " + cp);

        // tests jar installed -> packaged artifact wins (Maven parity)
        Path testsJar = repo.resolve("g/a/1.0/a-1.0-tests.jar");
        Files.writeString(testsJar, "jar");
        cp = m.testClasspath(main).entries();
        assertTrue(cp.contains(testsJar.toString()),
                "packaged tests artifact expected, got " + cp);
        assertTrue(!cp.contains("a/target/test-classes"),
                "empty sibling dir must not shadow the packaged tests jar, got " + cp);
    }

    /**
     * Regression (flink-agents observation, defect 23): an EXTERNAL test-jar
     * typed dependency (dep not in the reactor model) must contribute the
     * packaged tests artifact to the test classpath. The old code fell through
     * to the jar-only branch and silently DROPPED the entry — junit-vintage
     * discovery then died with NoClassDefFoundError on the first harness class
     * (flink-agents-runtime: KeyedOneInputStreamOperatorTestHarness), aborting
     * the whole module run where Maven had run 784 tests.
     */
    @Test
    void testExternalTestJarTypedDepContributesTestsArtifact(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Path root = tmp.resolve("root");
        Files.createDirectories(repo.resolve("org/apache/flink/flink-streaming-java/2.3.0"));

        IbModule main = new IbModule();
        main.setGroupId("g");
        main.setArtifactId("main");
        main.setOutputDir("main/target/classes");
        main.setTestOutputDir("main/target/test-classes");
        main.setExternalArtifacts(List.of(
                "org.apache.flink:flink-streaming-java:jar:2.3.0:provided",
                "org.apache.flink:flink-streaming-java:test-jar:tests:2.3.0:test"));

        IbModel model = new IbModel();
        model.setRepoRoot(root.toString());
        model.setLocalRepo(repo.toString());
        model.addModule(main);

        ClasspathMaterializer m = new ClasspathMaterializer(model);
        // tests jar installed -> contributes a packaged tests artifact (Maven parity).
        // Resolution may pick the user's real common repo jar or the seeded one —
        // assert on the entry shape, not the exact path.
        Path testsJar = repo.resolve(
                "org/apache/flink/flink-streaming-java/2.3.0/flink-streaming-java-2.3.0-tests.jar");
        Files.writeString(testsJar, "jar");
        var cp = m.testClasspath(main).entries();
        assertTrue(cp.stream().anyMatch(e -> e.endsWith("flink-streaming-java-2.3.0-tests.jar")),
                "packaged external tests artifact expected, got " + cp);

        // absent tests jar (unique GA so no local repo can satisfy it) -> MISSING,
        // never a silent drop
        IbModule absent = new IbModule();
        absent.setGroupId("g");
        absent.setArtifactId("absent-main");
        absent.setOutputDir("absent-main/target/classes");
        absent.setTestOutputDir("absent-main/target/test-classes");
        absent.setExternalArtifacts(List.of("g:definitely-absent:test-jar:tests:9.9.9:test"));
        model.addModule(absent);
        var materialized = m.testClasspath(absent);
        assertTrue(materialized.entries().stream()
                .noneMatch(e -> e.contains("definitely-absent-9.9.9-tests.jar")),
                "no tests jar installed: entry must be absent, got " + materialized.entries());
        assertTrue(materialized.missing().stream()
                .anyMatch(x -> x.contains("definitely-absent-9.9.9-tests.jar")),
                "absent tests jar must be reported missing (no silent no-op), got "
                        + materialized.missing());
    }

    /**
     * Regression (zeebe observation, defect 25): a local repository using the
     * branch-scoped-local-repository SEGMENTED layout ({@code cached/} for
     * downloads, {@code installed/<branch>/} for project artifacts) must resolve
     * external jars — the plain g/a/v layout does not exist for zeebe deps and
     * the whole module used to fail with "classpath incomplete".
     */
    @Test
    void resolvesSegmentedLocalRepoLayouts(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        String rel = "io/camunda/zeebe-zip/1.0/zeebe-zip-1.0.jar";

        IbModel model = new IbModel();
        model.setLocalRepo(repo.toString());
        IbModule main = new IbModule();
        main.setGroupId("g");
        main.setArtifactId("main");
        model.addModule(main);
        ClasspathMaterializer m = new ClasspathMaterializer(model);

        // cached/ layout (third-party downloads)
        Path cached = repo.resolve("cached").resolve(rel);
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "jar");
        assertEquals(cached, m.externalJar("io.camunda", "zeebe-zip", "1.0", null));

        // installed/<branch>/ layout (project artifacts) resolves when no cached
        // copy exists for another GA; for the same GA the cached download wins
        // (plain -> cached -> installed order)
        Path branch = repo.resolve("installed/some-branch")
                .resolve(Path.of("io", "camunda", "zeebe-model", "1.0", "zeebe-model-1.0.jar"));
        Files.createDirectories(branch.getParent());
        Files.writeString(branch, "jar");
        assertEquals(branch, m.externalJar("io.camunda", "zeebe-model", "1.0", null));
        assertEquals(cached, m.externalJar("io.camunda", "zeebe-zip", "1.0", null));

        // plain layout (standard repo) still takes precedence when present
        Path plain = repo.resolve(rel);
        Files.createDirectories(plain.getParent());
        Files.writeString(plain, "jar");
        assertEquals(plain, m.externalJar("io.camunda", "zeebe-zip", "1.0", null));
    }
}
