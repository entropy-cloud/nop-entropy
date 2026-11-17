package io.nop.ib.model;

import io.nop.ib.NopIbException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rebuilds model.json from Maven's own output (IB-G2 adjudication: effective-pom
 * batch dump + per-module dependency:list + localRepository discovery, all through
 * the worktree's mvnq wrapper so the worktree local repo is respected). Read-only
 * with respect to build products — no build lock required.
 */
public final class ModelRefresher {
    /** [INFO] --- maven-dependency-plugin:...:list ... @ <artifactId> --- */
    private static final Pattern MODULE_ANCHOR =
            Pattern.compile("@\\s+(\\S+)\\s*---");
    /**
     * g:a:type:version:scope or g:a:type:classifier:version:scope; Maven appends
     * " (optional)" before the " -- module ..." note — optional is a transitive
     * cutoff, not a classpath exclusion (surefire still sees such deps), so the
     * line must parse and the artifact must stay on the fast-path classpath.
     */
    private static final Pattern ARTIFACT_LINE =
            Pattern.compile("^\\[INFO\\]\\s+([^:\\s]+):([^:\\s]+):([^:\\s]+):([^:\\s]+)(?::([^:\\s]+))?(?::([^:\\s]+))?(?:\\s+\\(optional\\))?(?:\\s+--.*)?$");

    private final Path repoRoot;
    private final Duration timeout;

    public ModelRefresher(Path repoRoot) {
        this(repoRoot, Duration.ofMinutes(15));
    }

    public ModelRefresher(Path repoRoot, Duration timeout) {
        this.repoRoot = repoRoot;
        this.timeout = timeout;
    }

    public IbModel refresh() {
        Path tmp = repoRoot.resolve(ModelStore.STATE_DIR).resolve("tmp");
        try {
            Files.createDirectories(tmp);
        } catch (IOException e) {
            throw new NopIbException("cannot create state dir " + tmp, e);
        }

        // 1. toolchain facts
        String versionOut = runMaven("--version");
        String mavenVersion = null;
        String jdkVersion = null;
        for (String line : versionOut.split("\n")) {
            if (mavenVersion == null && line.startsWith("Apache Maven"))
                mavenVersion = line.substring("Apache Maven".length()).trim();
            if (line.startsWith("Java version:"))
                jdkVersion = line.substring("Java version:".length()).trim();
        }
        if (mavenVersion == null || jdkVersion == null)
            throw new NopIbException("cannot parse './mvnw --version' output: " + versionOut);

        // 2. local repository path (worktree repo when going through mvnq)
        String localRepo = stripMavenEchoes(runMaven("-q", "help:evaluate",
                "-Dexpression=settings.localRepository", "-DforceStdout")).trim();
        if (localRepo.isEmpty())
            throw new NopIbException("empty settings.localRepository output");

        // 3. effective-pom batch dump (one reactor invocation, design 05 §5.1)
        Path dump = tmp.resolve("ib-effective.xml");
        runMaven("-q", "help:effective-pom", "-Doutput=" + dump.toAbsolutePath());

        // 4. per-module resolved artifacts (one reactor invocation).
        // wave 11 item 8: a reactor-wide resolution failure (e.g. an uninstalled
        // reactor SNAPSHOT sibling, observed on nop-spring-gateway → demo-service)
        // no longer hard-fails the whole refresh — the reactor pass degrades to
        // per-module resolution below.
        Map<String, List<String>> artifactsByModule;
        // wave 11 item 8: artifactIds whose dependency facts could not be
        // resolved — refresh still exits 0, the planner must fall back for them
        List<String> unresolved = new ArrayList<>();
        try {
            String depListOut = runMaven("dependency:list", "-DincludeScope=test",
                    "-Dsort=false");
            artifactsByModule = parseDependencyList(depListOut);
        } catch (NopIbException e) {
            artifactsByModule = new LinkedHashMap<>();
        }

        // 5. parse dump into modules
        EffectivePomParser parser = new EffectivePomParser(repoRoot.toString());
        List<IbModule> modules = parser.parse(dump.toFile());

        // 4b. coverage self-check: Maven 3.9's reactor dependency:list buffers the
        // per-module resolved blocks and can print them OUT OF ORDER relative to
        // their anchors (observed on OpenPDF), so index-paired attribution can be
        // wrong. Verify each module's EA contains at least one of its own pom
        // direct deps; on mismatch, re-resolve per module (-pl :artifactId —
        // single-module output is unambiguous). Maven 4 output passes the check
        // and keeps the fast single-invocation path.
        Set<String> suspect = coverageSuspects(modules, artifactsByModule);
        if (!suspect.isEmpty()) {
            List<String> suspectFailures = new ArrayList<>();
            artifactsByModule = resolvePerModule(suspect, artifactsByModule, suspectFailures);
            for (String f : suspectFailures)
                if (!unresolved.contains(f))
                    unresolved.add(f);
        }

        // 4c. wave 11 item 8: every module still without resolved artifacts gets
        // one individual -pl attempt (this covers both the degraded reactor pass
        // and reactor passes that skipped anchors). Modules that STILL fail are
        // marked depsUnresolved — refresh exits 0, but the planner must route
        // any plan touching them to the Maven fallback (fail-safe: the closure
        // is not trustworthy).
        for (IbModule mod : modules) {
            if (mod.getBasedir() != null && !artifactsByModule.containsKey(mod.getArtifactId())) {
                try {
                    Map<String, List<String>> one = parseDependencyList(runMaven(
                            "dependency:list", "-DincludeScope=test", "-Dsort=false",
                            "-pl", ":" + mod.getArtifactId()));
                    artifactsByModule.put(mod.getArtifactId(),
                            one.getOrDefault(mod.getArtifactId(), new ArrayList<>()));
                } catch (NopIbException e) {
                    unresolved.add(mod.getArtifactId());
                }
            }
        }

        IbModel model = new IbModel();
        model.setRepoRoot(repoRoot.toString());
        model.setMavenVersion(mavenVersion);
        model.setJdkVersion(jdkVersion);
        model.setLocalRepo(localRepo);
        model.setDumpSha256(ModelStore.sha256(dump));
        model.setRefreshedAt(System.currentTimeMillis());
        model.setUnresolvedModules(unresolved);

        for (IbModule mod : modules) {
            if (mod.getBasedir() == null)
                continue; // root aggregator has no own source tree facts
            List<String> ext = artifactsByModule.get(mod.getArtifactId());
            if (ext != null)
                mod.setExternalArtifacts(ext);
            if (unresolved.contains(mod.getArtifactId()))
                mod.setDepsUnresolved(true);
            Path pom = repoRoot.resolve(mod.getBasedir()).resolve("pom.xml");
            mod.setPomHash(Files.isRegularFile(pom) ? ModelStore.sha256(pom) : null);
            model.addModule(mod);
        }
        model.getPomHashes().putAll(collectPomHashes(model));
        return model;
    }

    /** modules whose reactor-attributed EA misses any of their pom direct deps.
     *  A shared parent dep (e.g. the reactor artifact itself) appearing in a
     *  misattributed block is NOT enough — every direct dep must be present. */
    private Set<String> coverageSuspects(List<IbModule> modules,
                                         Map<String, List<String>> artifactsByModule) {
        Set<String> suspects = new LinkedHashSet<>();
        for (IbModule mod : modules) {
            List<String> ext = artifactsByModule.get(mod.getArtifactId());
            Set<String> direct = new LinkedHashSet<>();
            for (IbModule.DepEdge d : mod.getDependencies())
                direct.add(d.ga);
            if (direct.isEmpty() || ext == null)
                continue; // nothing to contradict (empty EA is legal for dep-free modules)
            Set<String> eas = new LinkedHashSet<>();
            for (String ea : ext) {
                int secondColon = ea.indexOf(':', ea.indexOf(':') + 1);
                eas.add(ea.substring(0, secondColon));
            }
            if (!eas.containsAll(direct))
                suspects.add(mod.getArtifactId());
        }
        return suspects;
    }

    /**
     * re-resolve suspect modules one -pl invocation each (unambiguous output).
     * wave 11 item 8: a per-module failure is tolerated — the module degrades
     * (empty EA, recorded in {@code failures} so the caller marks
     * {@code depsUnresolved}) instead of failing the whole refresh.
     */
    private Map<String, List<String>> resolvePerModule(Set<String> suspects,
                                                       Map<String, List<String>> artifactsByModule,
                                                       List<String> failures) {
        Map<String, List<String>> fixed = new LinkedHashMap<>(artifactsByModule);
        for (String artifactId : suspects) {
            Map<String, List<String>> one;
            try {
                String out = runMaven("dependency:list", "-DincludeScope=test",
                        "-Dsort=false", "-pl", ":" + artifactId);
                one = parseDependencyList(out);
            } catch (NopIbException e) {
                failures.add(artifactId);
                continue;
            }
            // single-module invocation: one anchor, one block — take it verbatim
            List<String> ea = one.getOrDefault(artifactId, new ArrayList<>());
            fixed.put(artifactId, ea);
        }
        return fixed;
    }

    private Map<String, String> collectPomHashes(IbModel model) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (IbModule mod : model.getModules()) {
            Path pom = repoRoot.resolve(mod.getBasedir()).resolve("pom.xml");
            if (Files.isRegularFile(pom))
                hashes.put(mod.getBasedir() + "/pom.xml", mod.getPomHash());
        }
        // plus any pom recorded from a previous model for modules that disappeared:
        // not applicable — pom set is derived from the fresh reactor.
        return hashes;
    }

    /**
     * mvnq's pipeline prefixes echoed child output with "[INFO] [stdout] ";
     * strip it wherever we expect clean tool output.
     */
    static String stripMavenEchoes(String text) {
        if (text.indexOf("[INFO] [stdout] ") < 0)
            return text;
        return text.replace("[INFO] [stdout] ", "");
    }

    Map<String, List<String>> parseDependencyList(String output) {
        Map<String, List<String>> byModule = new LinkedHashMap<>();
        List<String> anchors = new ArrayList<>();
        List<List<String>> blocks = new ArrayList<>();
        List<String> currentBlock = null;
        for (String line : output.split("\n")) {
            Matcher anchor = MODULE_ANCHOR.matcher(line);
            if (anchor.find() && line.contains(":list (default-cli)")) {
                anchors.add(anchor.group(1));
                continue;
            }
            if (line.contains("The following files have been resolved")) {
                // each execution emits exactly one resolved block; blocks and
                // anchors both appear in reactor order — Maven 3.9 buffers the
                // list output so it can lag its own anchor header (observed on
                // OpenPDF: anchor html followed by toolbox's block), so blocks
                // are paired with anchors BY INDEX, not adjacency
                currentBlock = new ArrayList<>();
                blocks.add(currentBlock);
                continue;
            }
            if (currentBlock == null)
                continue;
            Matcher artifact = ARTIFACT_LINE.matcher(line);
            if (artifact.matches()) {
                String group = artifact.group(1);
                String name = artifact.group(2);
                String type = artifact.group(3);
                String classifier = null;
                String version;
                String scope;
                if (artifact.group(6) != null) {
                    classifier = artifact.group(4);
                    version = artifact.group(5);
                    scope = artifact.group(6);
                } else {
                    version = artifact.group(4);
                    scope = artifact.group(5);
                }
                // pom-packaged aggregators (e.g. org.graalvm.js:js) are dependency
                // descriptors, not classpath entries — Maven's own classpath carries
                // only a .pom reference for them
                if ("pom".equals(type))
                    continue;
                currentBlock.add(group + ":" + name + ":" + type
                        + (classifier != null ? ":" + classifier : "")
                        + ":" + version + ":" + scope);
            }
        }
        if (anchors.size() == blocks.size()) {
            for (int i = 0; i < anchors.size(); i++)
                byModule.put(anchors.get(i), blocks.get(i));
        } else {
            // anchor/block counts diverge (aggregator with no deps, plugin
            // failure): fall back to adjacency, empty lists for the rest
            for (String a : anchors)
                byModule.put(a, new ArrayList<>());
            int idx = 0;
            for (List<String> block : blocks) {
                if (idx < anchors.size())
                    byModule.put(anchors.get(idx), block);
                idx++;
            }
        }
        return byModule;
    }

    /** bare mvn on PATH: the last fallback for plain Maven projects. */
    static String mvnOnPath() {
        for (String dir : System.getenv("PATH").split(":")) {
            Path mvn = Path.of(dir, "mvn");
            if (Files.isRegularFile(mvn))
                return mvn.toString();
        }
        return null;
    }

    private String runMaven(String... args) {
        Path mvnq = repoRoot.resolve("ai-dev/tools/mvnq");
        Path mvnw = repoRoot.resolve("mvnw");
        List<String> cmd = new ArrayList<>();
        if (Files.isRegularFile(mvnq)) {
            cmd.add("bash");
            cmd.add(mvnq.toAbsolutePath().toString());
        } else if (Files.isRegularFile(mvnw)) {
            cmd.add(mvnw.toAbsolutePath().toString());
        } else if (mvnOnPath() != null) {
            cmd.add(mvnOnPath());
        } else {
            throw new NopIbException("no mvnq/mvnw/mvn available under " + repoRoot);
        }
        for (String arg : args)
            cmd.add(arg);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread outThread = drain(process.inputReader(StandardCharsets.UTF_8), out);
            Thread errThread = drain(process.errorReader(StandardCharsets.UTF_8), err);
            boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new NopIbException("maven invocation timed out after " + timeout
                        + ": " + args[0]);
            }
            outThread.join(5000);
            errThread.join(5000);
            if (process.exitValue() != 0)
                throw new NopIbException("maven invocation failed (exit " + process.exitValue()
                        + "): " + String.join(" ", args) + "\n" + err);
            return out.toString();
        } catch (IOException e) {
            throw new NopIbException("failed to launch maven: " + String.join(" ", args), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopIbException("interrupted while running maven");
        }
    }

    private Thread drain(BufferedReader reader, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = reader) {
                String line;
                while ((line = r.readLine()) != null)
                    sink.append(line).append('\n');
            } catch (IOException ignored) {
                // stream closed by process teardown
            }
        }, "nop-ib-mvn-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
