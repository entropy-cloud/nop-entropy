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

    /** modules per chunked `dependency:list -pl` invocation (one Maven JVM each). */
    static final int RESOLVE_CHUNK = 16;

    /**
     * Publishes the refresh phase into the service progress registry so a long
     * refresh is OBSERVABLE over GET /v1/job/{id} and the long-poll API — a
     * multi-minute Maven subprocess that reports nothing is indistinguishable
     * from a hang (the defect that motivated this). Publishing is best-effort:
     * the registry lives in the service package and is a no-op map write in
     * plain-CLI runs.
     */
    private void phase(String phase) {
        try {
            io.nop.ib.service.ProgressRegistry.update(repoRoot, java.util.Map.of(
                    "phase", phase,
                    "elapsedMs", System.currentTimeMillis() - refreshStart));
        } catch (Throwable ignored) {
            // plain CLI run without the service layer — never fail a refresh on progress
        }
    }
    private long refreshStart;

    public ModelRefresher(Path repoRoot) {
        this(repoRoot, Duration.ofMinutes(15));
    }

    public ModelRefresher(Path repoRoot, Duration timeout) {
        this.repoRoot = repoRoot;
        this.timeout = timeout;
    }

    public IbModel refresh() {
        refreshStart = System.currentTimeMillis();
        phase("refresh: starting");
        Path tmp = repoRoot.resolve(ModelStore.STATE_DIR).resolve("tmp");
        try {
            Files.createDirectories(tmp);
        } catch (IOException e) {
            throw new NopIbException("cannot create state dir " + tmp, e);
        }

        // 1. toolchain facts
        phase("refresh: maven --version");
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

        // 2. local repository path (worktree repo when going through mvnq).
        // NOTE: must stay a standalone invocation — Maven 4 drops the
        // help:evaluate forceStdout output when another goal follows in the
        // same command (verified 2026-11-21), so goal-chaining here silently
        // breaks the localRepository fact.
        phase("refresh: resolve localRepository");
        String localRepo = stripMavenEchoes(runMaven("-q", "help:evaluate",
                "-Dexpression=settings.localRepository", "-DforceStdout")).trim();
        if (localRepo.isEmpty())
            throw new NopIbException("empty settings.localRepository output");

        // 3. effective-pom batch dump (one reactor invocation, design 05 §5.1)
        phase("refresh: effective-pom dump");
        Path dump = tmp.resolve("ib-effective.xml");
        runMaven("-q", "help:effective-pom", "-Doutput=" + dump.toAbsolutePath());

        // 4. per-module resolved artifacts (one reactor invocation).
        // wave 11 item 8: a reactor-wide resolution failure (e.g. an uninstalled
        // reactor SNAPSHOT sibling, observed on nop-spring-gateway → demo-service)
        // no longer hard-fails the whole refresh — the reactor pass degrades to
        // chunked per-module resolution below.
        phase("refresh: dependency:list (reactor)");
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
        // direct deps; on mismatch, re-resolve. Re-resolution used to be one
        // `dependency:list -pl :artifactId` Maven run PER suspect module — on a
        // 100-module reactor that is up to 100 serial JVM startups (minutes to
        // HOURS on the 1/10-performance hosts this service targets, and it
        // looked like a hang because nothing reported progress). Chunks of
        // `-pl :a,:b,...` keep one JVM per 16 modules; only modules that still
        // fail the coverage check after chunking get their own invocation.
        Set<String> suspect = coverageSuspects(modules, artifactsByModule);
        if (!suspect.isEmpty()) {
            phase("refresh: re-resolve " + suspect.size() + " suspect modules (chunked)");
            List<String> suspectFailures = new ArrayList<>();
            artifactsByModule = resolveModules(suspect, artifactsByModule, suspectFailures);
            for (String f : suspectFailures)
                if (!unresolved.contains(f))
                    unresolved.add(f);
        }

        // 4c. wave 11 item 8: every module still without resolved artifacts gets
        // one attempt (chunked — covers both the degraded reactor pass and
        // reactor passes that skipped anchors). Modules that STILL fail are
        // marked depsUnresolved — refresh exits 0, but the planner must route
        // any plan touching them to the Maven fallback (fail-safe: the closure
        // is not trustworthy).
        Set<String> missing = new LinkedHashSet<>();
        for (IbModule mod : modules)
            if (mod.getBasedir() != null && !artifactsByModule.containsKey(mod.getArtifactId()))
                missing.add(mod.getArtifactId());
        if (!missing.isEmpty()) {
            phase("refresh: resolve " + missing.size() + " missing modules (chunked)");
            List<String> missingFailures = new ArrayList<>();
            artifactsByModule = resolveModules(missing, artifactsByModule, missingFailures);
            for (String f : missingFailures)
                if (!unresolved.contains(f))
                    unresolved.add(f);
        }

        IbModel model = new IbModel();
        phase("refresh: assembling model");
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
        phase("refresh: done (" + model.getModules().size() + " modules, "
                + (System.currentTimeMillis() - refreshStart) + "ms)");
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
     * Re-resolve the given modules with chunked `-pl :a,:b,...` invocations —
     * one Maven JVM per {@link #RESOLVE_CHUNK} modules instead of one per
     * module. Modules whose chunk still fails the coverage check afterwards get
     * one single-module invocation each (unambiguous output); a final failure
     * is tolerated — the module degrades (empty EA, recorded in {@code
     * failures} so the caller marks {@code depsUnresolved}) instead of failing
     * the whole refresh.
     */
    private Map<String, List<String>> resolveModules(Set<String> moduleIds,
                                                     Map<String, List<String>> artifactsByModule,
                                                     List<String> failures) {
        Map<String, List<String>> fixed = new LinkedHashMap<>(artifactsByModule);
        moduleIds.forEach(fixed::remove); // force re-resolution, not stale reuse
        List<String> todo = new ArrayList<>(moduleIds);
        for (int from = 0; from < todo.size(); from += RESOLVE_CHUNK) {
            List<String> chunk = todo.subList(from, Math.min(todo.size(), from + RESOLVE_CHUNK));
            try {
                Map<String, List<String>> got = parseDependencyList(runMaven(
                        "dependency:list", "-DincludeScope=test", "-Dsort=false",
                        "-pl", ":" + String.join(",:", chunk)));
                for (String id : chunk)
                    fixed.put(id, got.getOrDefault(id, fixed.getOrDefault(id, new ArrayList<>())));
            } catch (NopIbException e) {
                failures.addAll(chunk);
            }
        }
        // second chance: single-module invocations for modules without usable facts
        for (String artifactId : new ArrayList<>(moduleIds)) {
            if (failures.contains(artifactId)
                    && !fixed.containsKey(artifactId)) {
                try {
                    Map<String, List<String>> one = parseDependencyList(runMaven(
                            "dependency:list", "-DincludeScope=test", "-Dsort=false",
                            "-pl", ":" + artifactId));
                    fixed.put(artifactId, one.getOrDefault(artifactId, new ArrayList<>()));
                    failures.remove(artifactId);
                } catch (NopIbException e) {
                    // keep in failures
                }
            }
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
            String joined = String.join(" ", args);
            phase("maven subprocess: "
                    + (joined.length() > 120 ? joined.substring(0, 120) + "..." : joined));
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
