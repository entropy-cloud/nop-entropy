package io.nop.ib.compile;

import io.nop.ib.NopIbException;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

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
            if (parts.length < 3)
                continue;
            String ga = parts[0] + ":" + parts[1];
            String version = parts[parts.length - 2];
            String scope = parts[parts.length - 1];
            boolean intra = model.module(parts[1]) != null
                    && model.module(parts[1]).getGa().equals(ga);
            if (intra) {
                IbModule dep = model.module(parts[1]);
                if ("test".equals(scope) && test && dep.getTestOutputDir() != null)
                    seen.add(dep.getTestOutputDir());
                if (dep.getOutputDir() != null)
                    seen.add(dep.getOutputDir());
                continue;
            }
            boolean compileScope = "compile".equals(scope) || "provided".equals(scope)
                    || ("runtime".equals(scope) && test)
                    || (test && !"provided".equals(scope) && !"system".equals(scope));
            if (!compileScope)
                continue;
            Path jar = externalJar(parts[0], parts[1], version,
                    parts.length == 6 ? parts[3] : null);
            if (!Files.isRegularFile(jar))
                missing.add(jar.toString());
            else
                seen.add(jar.toString());
        }

        // reactor directories become real classpath entries in stable order
        entries.addAll(seen);
        return new Materialized(entries, missing);
    }

    private Path externalJar(String group, String artifact, String version, String classifier) {
        String groupPath = group.replace('.', '/');
        StringBuilder name = new StringBuilder(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty())
            name.append('-').append(classifier);
        name.append(".jar");
        return Path.of(model.getLocalRepo(), groupPath, artifact, version, name.toString());
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
