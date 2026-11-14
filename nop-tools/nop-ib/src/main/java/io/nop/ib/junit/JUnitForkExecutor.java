package io.nop.ib.junit;

import io.nop.ib.NopIbException;
import io.nop.ib.compile.ClasspathMaterializer;
import io.nop.ib.json.JsonIn;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Parent-side orchestrator for the forked JUnit JVM (design 02-test-execution §三):
 * cwd = module basedir; classpath = materialized ordered entries (test-classes →
 * classes → deps), never merged across modules; JVM options = the module's
 * effective argLine (unresolvable ${...} placeholders are dropped with a warning —
 * they belong to Maven profile machinery like jacoco); snapshot recording is
 * explicitly disabled; batch watchdog kills a hung fork.
 *
 * <p>The JUnit platform artifacts come from the module's own resolved test
 * classpath; nop-ib only adds the version-matched junit-platform-launcher jar
 * (version read from junit-platform-commons on that classpath) plus the runner
 * class directory.
 */
public final class JUnitForkExecutor {
    /** per-fork batch timeout (design 02 §七); configurable per run. */
    public static final Duration DEFAULT_BATCH_TIMEOUT =
            Duration.ofSeconds(Long.getLong("nop.ib.forkTimeoutSeconds", 300));

    private final IbModel model;
    private Duration timeout = DEFAULT_BATCH_TIMEOUT;

    public JUnitForkExecutor(IbModel model) {
        this.model = model;
    }

    public JUnitForkExecutor timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public record ForkResult(int exitCode, Map<String, Object> summary, List<String> warnings) {
    }

    public ForkResult execute(IbModule module, ClasspathMaterializer.Materialized classpath,
                              List<String> testClasses) {
        return execute(module, classpath, testClasses, timeout);
    }

    /**
     * Per-call timeout: the differential rerun derives it from the worker-measured
     * class time (design 06 WR-D4) — this executor instance is shared across
     * concurrent retries, so a mutable field would race.
     */
    public ForkResult execute(IbModule module, ClasspathMaterializer.Materialized classpath,
                              List<String> testClasses, Duration batchTimeout) {
        List<String> warnings = new ArrayList<>();
        String platformVersion = detectPlatformVersion(classpath.entries());
        if (platformVersion == null) {
            warnings.add("junit-platform-commons not found on module test classpath");
            return new ForkResult(-3, Map.of(), warnings);
        }
        Path launcherJar = locateLauncherJar(platformVersion);
        if (launcherJar == null) {
            warnings.add("junit-platform-launcher " + platformVersion + " missing in local repos");
            return new ForkResult(-3, Map.of(), warnings);
        }
        Path nopIbClasses = Path.of(model.getLocalRepo()).getParent() == null
                ? null : locateRunnerClasses();
        if (nopIbClasses == null) {
            warnings.add("nop-ib runner classes not found");
            return new ForkResult(-3, Map.of(), warnings);
        }

        Path resultFile;
        try {
            resultFile = Files.createTempFile("nop-ib-junit-", ".json");
        } catch (IOException e) {
            throw new NopIbException("cannot create junit result temp file", e);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaHomeBin("java"));
        // module argLine from the model; unresolvable ${...} tokens are dropped
        // with a per-token warning (they belong to Maven profile machinery)
        String argLine = module.getSurefireArgLine();
        if (argLine != null && !argLine.isBlank()) {
            for (String token : argLine.trim().split("\\s+")) {
                if (token.contains("${")) {
                    warnings.add("argLine token dropped (unresolvable placeholder): " + token);
                    continue;
                }
                cmd.add(token);
            }
        }
        cmd.add("-Dnop.autotest.force-save-output=false");
        cmd.add("-Dnop.autotest.disable-snapshot=false");
        module.getSurefireSystemProperties().forEach((k, v) -> cmd.add("-D" + k + "=" + v));
        cmd.add("-cp");
        // fork cwd = module basedir; classpath entries are repo-relative -> absolutize
        Path repoRoot = Path.of(model.getRepoRoot());
        List<String> cp = new ArrayList<>();
        for (String entry : classpath.entries()) {
            Path p = Path.of(entry);
            cp.add(p.isAbsolute() ? entry : repoRoot.resolve(entry).toString());
        }
        // surefire normally supplies the platform runtime set at runtime; the fork
        // needs it explicitly, version-matched to the module classpath. Three
        // pieces: junit-jupiter-engine (test API implementation), and — when the
        // module only declares the API split (e.g. jupiter-api without the
        // jupiter aggregate, as OpenPDF does) — junit-platform-engine, which the
        // engine requires and which OutputDirectoryCreator et al. live in.
        if (cp.stream().noneMatch(e -> e.contains("junit-jupiter-engine"))) {
            String jupiterVersion = detectArtifactVersion(cp, "junit-jupiter-");
            if (jupiterVersion != null) {
                Path engineJar = locateArtifactJar("org.junit.jupiter", "junit-jupiter-engine",
                        jupiterVersion, null);
                if (engineJar != null)
                    cp.add(engineJar.toString());
                else
                    warnings.add("junit-jupiter-engine " + jupiterVersion
                            + " not found in local repos - engine missing, tests cannot run");
            } else {
                warnings.add("junit-jupiter version not detectable on classpath");
            }
        }
        if (cp.stream().noneMatch(e -> e.contains("junit-platform-engine"))) {
            Path platformEngine = locateArtifactJar("org.junit.platform", "junit-platform-engine",
                    platformVersion, null);
            if (platformEngine != null)
                cp.add(platformEngine.toString());
            else
                warnings.add("junit-platform-engine " + platformVersion
                        + " not found in local repos - tests cannot run");
        }
        // guard: a launcher without any TestEngine fails at boot — fall back to
        // surefire instead of a doomed fork (JUnit4-only or exotic stacks)
        if (cp.stream().noneMatch(e -> e.contains("junit-platform-engine"))) {
            warnings.add("no JUnit Platform engine assemblable from module classpath");
            return new ForkResult(-3, Map.of(), warnings);
        }
        cp.add(launcherJar.toString());
        cp.add(nopIbClasses.toString());
        cmd.add(String.join(":", cp));
        cmd.add(IbJUnitRunner.class.getName());
        cmd.add(resultFile.toAbsolutePath().toString());
        cmd.addAll(testClasses);

        if (System.getenv().keySet().stream().anyMatch(k -> k.startsWith("nop.")))
            warnings.add("nop.* environment variables present: they override fork system properties");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(model.resolve(module.getBasedir())); // cwd = module basedir, 硬约束
        pb.redirectErrorStream(false);
        try {
            Process process = pb.start();
            StringBuilder forkErr = new StringBuilder();
            Thread errT = new Thread(() -> {
                try (var r = process.errorReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = r.readLine()) != null)
                        forkErr.append(line).append('\n');
                } catch (IOException ignored) {
                    // stream closed at process teardown
                }
            }, "nop-ib-junit-err");
            errT.setDaemon(true);
            errT.start();
            // stdout MUST be drained continuously: a chatty test class fills the
            // 64KB pipe and the fork deadlocks on System.out.write until the batch
            // watchdog kills it — this path feeds the differential rerun, so the
            // deadlock used to turn every noisy failed class into a 300s stall.
            // The runner's result document travels via the temp file, not stdout.
            java.util.concurrent.atomic.AtomicReference<String> outTail =
                    new java.util.concurrent.atomic.AtomicReference<>("");
            Thread outT = new Thread(() -> {
                try (var r = process.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        outTail.accumulateAndGet(line, (cur, l) -> {
                            String next = cur + l + "\n";
                            return next.length() > 4096
                                    ? next.substring(next.length() - 4096) : next;
                        });
                    }
                } catch (IOException ignored) {
                    // stream closed at process teardown
                }
            }, "nop-ib-junit-out");
            outT.setDaemon(true);
            outT.start();

            boolean finished = process.waitFor(batchTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                warnings.add("fork killed by batch watchdog after " + batchTimeout.toSeconds() + "s");
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("tests", 0);
                summary.put("failures", 1);
                summary.put("timedOut", true);
                return new ForkResult(1, summary, warnings);
            }
            errT.join(2000);

            Map<String, Object> summary = new LinkedHashMap<>();
            if (Files.isRegularFile(resultFile)) {
                summary = JsonIn.asMapOrEmpty(JsonIn.silentParse(Files.readString(resultFile)));
            } else {
                summary.put("tests", 0);
                summary.put("failures", process.exitValue() == 0 ? 0 : 1);
                summary.put("crashed", true);
                if (forkErr.length() > 0) {
                    String errText = forkErr.toString();
                    summary.put("forkError", errText.length() > 2000 ? errText.substring(0, 2000) : errText);
                }
                if (!outTail.get().isEmpty()) {
                    String outText = outTail.get();
                    summary.put("forkStdoutTail", outText.length() > 2000
                            ? outText.substring(outText.length() - 2000) : outText);
                }
                warnings.add("fork produced no result document (exit " + process.exitValue() + ")");
            }
            Files.deleteIfExists(resultFile);
            int exit = process.exitValue();
            if (exit != 0 && exit != 1 && exit != 2)
                warnings.add("unexpected fork exit code: " + exit);
            return new ForkResult(exit, summary, warnings);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            throw new NopIbException("failed to run junit fork for " + module.getArtifactId(), e);
        }
    }

    /** junit-platform-commons-<version>.jar on the materialized classpath → version. */
    static String detectPlatformVersion(List<String> entries) {
        return detectArtifactVersion(entries, "junit-platform-commons-");
    }

    /** first artifact with the given filename prefix on the classpath → its version.
     *  The artifact id itself may extend the prefix (junit-jupiter-API-6.1.0.jar):
     *  the version is the trailing token starting at the first digit. */
    static String detectArtifactVersion(List<String> entries, String prefix) {
        for (String entry : entries) {
            String name = entry.substring(entry.lastIndexOf('/') + 1);
            if (name.startsWith(prefix) && name.endsWith(".jar")) {
                String rest = name.substring(prefix.length(), name.length() - ".jar".length());
                int firstDigit = -1;
                for (int i = 0; i < rest.length(); i++) {
                    if (Character.isDigit(rest.charAt(i))) {
                        firstDigit = i;
                        break;
                    }
                }
                return firstDigit >= 0 ? rest.substring(firstDigit) : rest;
            }
        }
        return null;
    }

    /** common repo first (read-only), then the worktree repo fallback. */
    Path locateArtifactJar(String group, String artifact, String version, String classifier) {
        return locateArtifactJar(group, artifact, version, classifier, null);
    }

    Path locateArtifactJar(String group, String artifact, String version, String classifier,
                           List<String> entries) {
        if (entries != null) {
            String jarName = artifact + "-" + version
                    + (classifier != null && !classifier.isEmpty() ? "-" + classifier : "") + ".jar";
            for (String entry : entries) {
                if (entry.endsWith("/" + jarName) || entry.endsWith(jarName))
                    return Path.of(entry);
            }
        }
        String rel = relativeRepoPath(group, artifact, version, classifier);
        String common = commonRepo();
        Path jar = Path.of(common, rel);
        if (Files.isRegularFile(jar))
            return jar;
        jar = Path.of(model.getLocalRepo(), rel);
        return Files.isRegularFile(jar) ? jar : null;
    }

    private String commonRepo() {
        String env = System.getenv("NOP_IB_COMMON_REPO");
        if (env != null && !env.isBlank())
            return env;
        return Path.of(System.getProperty("user.home"), ".m2", "repository").toString();
    }

    private String relativeRepoPath(String group, String artifact, String version, String classifier) {
        StringBuilder name = new StringBuilder(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty())
            name.append('-').append(classifier);
        name.append(".jar");
        return Path.of(group.replace('.', '/'), artifact, version, name.toString()).toString();
    }

    Path locateLauncherJar(String version) {
        return locateArtifactJar("org.junit.platform", "junit-platform-launcher", version, null);
    }

    /** the runner must live on the fork classpath; nop-ib's own classes dir is it. */
    private Path locateRunnerClasses() {
        // IbJUnitRunner.class protection domain = nop-tools/nop-ib/target/classes
        var pd = IbJUnitRunner.class.getProtectionDomain();
        if (pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
            Path p = Path.of(pd.getCodeSource().getLocation().getPath());
            if (Files.isDirectory(p))
                return p;
        }
        return null;
    }

    private static String javaHomeBin(String tool) {
        String javaHome = System.getProperty("java.home");
        return javaHome != null ? Path.of(javaHome, "bin", tool).toString() : tool;
    }
}
