package io.nop.ib.compile;

import io.nop.ib.NopIbException;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fast-path classpath materialization (design 01 §11.3, IB-D7): intra-project
 * dependencies are replaced by their reactor output directories — no
 * intra-project jar may ever appear on a fast-path classpath — and external
 * artifacts resolve against the model's local repository in dependency order.
 */
public final class ClasspathMaterializer {
    private final IbModel model;

    public ClasspathMaterializer(IbModel model) {
        this.model = model;
    }

    /** main-compile classpath: compile+provided externals + reactor main dirs. */
    public Materialized mainClasspath(IbModule module) {
        return materialize(module, false);
    }

    /** test-compile classpath: test output first, then main output, then all deps. */
    public Materialized testClasspath(IbModule module) {
        return materialize(module, true);
    }

    private Materialized materialize(IbModule module, boolean test) {
        List<String> entries = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (test && module.getTestOutputDir() != null)
            seen.add(module.getTestOutputDir());
        if (module.getOutputDir() != null)
            seen.add(module.getOutputDir());

        for (String artifact : module.getExternalArtifacts()) {
            String[] parts = artifact.split(":");
            if (parts.length < 4)
                continue;
            String ga = parts[0] + ":" + parts[1];
            String type = parts[2];
            String version = parts[parts.length - 2];
            String scope = parts[parts.length - 1];
            boolean intra = model.module(parts[1]) != null
                    && model.module(parts[1]).getGa().equals(ga);
            if (intra) {
                IbModule dep = model.module(parts[1]);
                // surefire semantics: a plain test-scoped dependency contributes its
                // MAIN products only — dependency test-classes enter the classpath
                // exclusively through an explicit test-jar typed dependency. Adding
                // dep.testOutputDir for every test-scoped dep pulled dozens of
                // foreign test-classes dirs (and their processes/ resources) into
                // Spring auto-deployment (Activiti Duplicate key observation).
                boolean testJar = "test-jar".equals(type);
                if (testJar && test) {
                    // Maven semantics (flink observation): an explicit test-jar typed
                    // dependency resolves to the PACKAGED tests artifact from the local
                    // repository — not to the sibling's target/test-classes. The dir
                    // mapping is only a fallback for the not-yet-installed dev case:
                    // a skipped/never-compiled sibling leaves target/test-classes empty
                    // (flink -Dmaven.test.skip install) and an empty dir silently drops
                    // every test-harness class -> NoClassDefFoundError at discovery.
                    String depGroupId = dep.getGa().split(":")[0];
                    Path testsJar = externalJar(depGroupId, parts[1], version, "tests");
                    if (Files.isRegularFile(testsJar))
                        seen.add(testsJar.toString());
                    else if (dep.getTestOutputDir() != null)
                        seen.add(dep.getTestOutputDir());
                } else if (!testJar && dep.getOutputDir() != null)
                    seen.add(dep.getOutputDir());
                continue;
            }
            boolean compileScope = "compile".equals(scope) || "provided".equals(scope)
                    || ("runtime".equals(scope) && test)
                    || (test && !"provided".equals(scope) && !"system".equals(scope));
            if (!compileScope)
                continue;
            if ("test-jar".equals(type)) {
                // external test-jar typed dependency (flink-agents observation,
                // defect 23): resolve the PACKAGED tests artifact from the local
                // repository like Maven does. The old code fell through to the
                // jar-only branch and silently DROPPED the entry — junit discovery
                // then died on NoClassDefFoundError for the first harness class
                // (flink-agents-runtime: KeyedOneInputStreamOperatorTestHarness).
                Path testsJar = externalJar(parts[0], parts[1], version,
                        parts.length == 6 ? parts[3] : "tests");
                if (!Files.isRegularFile(testsJar))
                    missing.add(testsJar.toString());
                else
                    seen.add(testsJar.toString());
                continue;
            }
            // g:a:type[:classifier]:version:scope — non-jar packaging (pom, bundle
            // manifests handled at refresh) never yields a classpath file
            if (!"jar".equals(type))
                continue;
            Path jar = externalJar(parts[0], parts[1], version,
                    parts.length == 6 ? parts[3] : null);
            if (!Files.isRegularFile(jar))
                missing.add(jar.toString());
            else
                seen.add(jar.toString());
        }

        // reactor directories and external jars become classpath entries in stable order
        entries.addAll(seen);
        return new Materialized(entries, missing);
    }

    /**
     * External (third-party) artifacts are resolved READ-ONLY from the common
     * repository first — the common repo is the single source of truth for
     * third-party dependencies and is never written by nop-ib. The worktree-local
     * repo is only a fallback for artifacts the cold path happened to download
     * there (e.g. versioned snapshots); project-built artifacts never take this
     * path at all (intra-project deps are reactor directories, see above).
     */
    Path externalJar(String group, String artifact, String version, String classifier) {
        String rel = relativePath(group, artifact, version, classifier);
        String common = commonRepo();
        if (common != null) {
            Path jar = segmentedJar(Path.of(common), rel);
            if (jar != null)
                return jar;
        }
        Path local = segmentedJar(Path.of(model.getLocalRepo()), rel);
        return local != null ? local : Path.of(model.getLocalRepo(), rel);
    }

    /**
     * Resolve a jar under a local repository that may use a SEGMENTED layout
     * (zeebe observation, defect 25): the branch-scoped-local-repository Maven
     * extension installs project artifacts under {@code installed/<branch>/} and
     * caches downloads under {@code cached/} — the plain {@code g/a/v} layout
     * does not exist for either. Resolution order: plain layout first, then the
     * segmented layouts; absent everywhere -> null (caller reports missing).
     */
    private Path segmentedJar(Path repo, String rel) {
        Path plain = repo.resolve(rel);
        if (Files.isRegularFile(plain))
            return plain;
        Path cached = repo.resolve("cached").resolve(rel);
        if (Files.isRegularFile(cached))
            return cached;
        Path installed = repo.resolve("installed");
        if (Files.isDirectory(installed)) {
            try (var stream = Files.list(installed)) {
                var hit = stream.filter(Files::isDirectory)
                        .map(branch -> branch.resolve(rel))
                        .filter(Files::isRegularFile)
                        .findFirst();
                if (hit.isPresent())
                    return hit.get();
            } catch (IOException e) {
                // unreadable installed dir: fall through to absent
            }
        }
        return null;
    }

    private String commonRepo() {
        String env = System.getenv("NOP_IB_COMMON_REPO");
        if (env != null && !env.isBlank())
            return env;
        return Path.of(System.getProperty("user.home"), ".m2", "repository").toString();
    }

    private String relativePath(String group, String artifact, String version, String classifier) {
        String groupPath = group.replace('.', '/');
        StringBuilder name = new StringBuilder(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty())
            name.append('-').append(classifier);
        name.append(".jar");
        return Path.of(groupPath, artifact, version, name.toString()).toString();
    }

    public record Materialized(List<String> entries, List<String> missing) {
        public boolean complete() {
            return missing.isEmpty();
        }

        public String joined() {
            return String.join(":", entries);
        }

        /** hard assertion per design 01 §11.3: no intra-project jar may remain. */
        public void assertNoIntraProjectJars(IbModel model) {
            for (String entry : entries) {
                if (entry.endsWith(".jar") && entry.contains(model.getLocalRepo())) {
                    // external jars live in the local repo; intra-project artifacts of this
                    // reactor are replaced before this assertion can see them
                    for (IbModule m : model.getModules()) {
                        if (entry.contains("/" + m.getArtifactId() + "-" + m.getVersion() + ".jar")) {
                            throw new NopIbException(
                                    "intra-project jar leaked into fast-path classpath: " + entry);
                        }
                    }
                }
            }
        }
    }
}
