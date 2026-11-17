package io.nop.ib.tasks;

import io.nop.ib.NopIbException;
import io.nop.ib.compile.ClasspathMaterializer;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes one declared code-generation task against one module (design 08 §三):
 * fingerprint skip → classpath materialization (IB-T2) → forked JVM exec
 * (IB-T3) → outputs existence validation → fingerprint write. Every failure
 * mode returns {@code ok=false} with a reason — there is no silent-skip path
 * (IB-T6).
 */
public final class TaskRunner {
    /** task watchdog (design 08 §三); generous default, sysprop overridable */
    private static final Duration TIMEOUT = Duration.ofMillis(
            Long.getLong("nop.ib.taskTimeoutMillis", 300_000));

    private final IbModel model;
    private final Path repoRoot;
    /** recorded outcomes of this runner instance, for the run feedback contract */
    private final List<Map<String, Object>> record = new ArrayList<>();

    public TaskRunner(IbModel model, Path repoRoot) {
        this.model = model;
        this.repoRoot = repoRoot;
    }

    public List<Map<String, Object>> getRecord() {
        return record;
    }

    public record TaskResult(boolean ok, String kind, String reason, String stderrTail) {
    }

    /**
     * Runs one task for one module. kind ∈ skipped|executed (on ok), failed
     * otherwise; reason carries the failure summary; stderrTail the last lines
     * of a failed exec.
     */
    public TaskResult run(IbModule module, TaskConfig task) {
        Path moduleDir = moduleBasedir(module);
        try {
            return runChecked(module, task, moduleDir);
        } catch (Exception e) {
            String reason = "task '" + task.name + "' setup failed: " + e;
            recordTask(task.name, "failed", reason);
            return new TaskResult(false, "failed", reason, null);
        }
    }

    private TaskResult runChecked(IbModule module, TaskConfig task, Path moduleDir) throws Exception {
        List<String> inputFiles = expand(moduleDir, task.inputs);
        List<String> outputsPresent = expand(moduleDir, task.outputs);
        String inputsHash = hashInputs(moduleDir, inputFiles);

        TaskFingerprint fp = TaskFingerprint.load(moduleDir, task.name);
        if (fp != null && fp.matches(inputsHash, outputsPresent)) {
            recordTask(task.name, "skipped", null);
            return new TaskResult(true, "skipped", null, null);
        }

        // classpath materialization (IB-T2): compile/runtime -> main, test -> test
        ClasspathMaterializer materializer = new ClasspathMaterializer(model);
        ClasspathMaterializer.Materialized cp = "test".equals(task.exec.classpathScope)
                ? materializer.testClasspath(module)
                : materializer.mainClasspath(module);
        if (!cp.complete()) {
            return fail(task, "classpath incomplete for task '" + task.name + "' in "
                    + module.getArtifactId() + "; missing: " + String.join(", ", cp.missing()));
        }

        List<String> cmd = buildCommand(task, cp.entries().stream()
                .map(e -> repoRoot.resolve(e).toString())
                .reduce((a, b) -> a + ":" + b).orElse(""), moduleDir);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(moduleDir.toFile());
        pb.redirectErrorStream(false);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD); // stdout is not part of the contract; stderr is
        Process process = pb.start();
        byte[] stderr;
        try (var errStream = process.getErrorStream()) {
            stderr = errStream.readAllBytes();
        }
        boolean finished = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return fail(task, "task '" + task.name + "' timed out after " + TIMEOUT + "s (killed)");
        }
        int exit = process.exitValue();
        if (exit != 0) {
            String t = tail(stderr);
            return fail(task, "task '" + task.name + "' exited " + exit + " in "
                    + module.getArtifactId() + (t != null ? ";\nstderr tail:\n" + t : ""), t);
        }

        List<String> outputsAfter = expand(moduleDir, task.outputs);
        if (outputsAfter.isEmpty()) {
            return fail(task, "task '" + task.name + "' produced no outputs matching "
                    + String.join(", ", task.outputs));
        }
        new TaskFingerprint(inputsHash, outputsAfter).save(moduleDir, task.name);
        recordTask(task.name, "executed", null);
        return new TaskResult(true, "executed", null, null);
    }

    private List<String> buildCommand(TaskConfig task, String classpath, Path moduleDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaHome() + "/bin/java");
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(task.exec.mainClass);
        if ("xscript".equals(task.exec.type)) {
            cmd.add(moduleDir.resolve(task.exec.script).normalize().toString());
        }
        cmd.addAll(task.exec.arguments);
        return cmd;
    }

    private Path moduleBasedir(IbModule module) {
        String basedir = module.getBasedir();
        return (basedir == null || basedir.isEmpty()) ? repoRoot : repoRoot.resolve(basedir).normalize();
    }

    /** expands globs under moduleDir to repo-relative FILES (directories excluded) */
    private List<String> expand(Path moduleDir, List<String> globs) throws IOException {
        List<java.util.regex.Pattern> patterns = new ArrayList<>();
        for (String g : globs)
            patterns.add(GlobMatcher.compile(g));
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(moduleDir))
            return out;
        try (var stream = Files.walk(moduleDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String rel = moduleDir.relativize(p).toString().replace('\\', '/');
                for (var pat : patterns) {
                    if (GlobMatcher.matches(pat, rel)) {
                        out.add(rel);
                        break;
                    }
                }
            });
        }
        out.sort(String::compareTo);
        return out;
    }

    private String hashInputs(Path moduleDir, List<String> inputFiles) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String rel : inputFiles) {
            md.update(rel.getBytes(StandardCharsets.UTF_8));
            md.update(Files.readAllBytes(moduleDir.resolve(rel)));
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private TaskResult fail(TaskConfig task, String reason) {
        return fail(task, reason, null);
    }

    private TaskResult fail(TaskConfig task, String reason, String stderrTail) {
        recordTask(task.name, "failed", reason);
        return new TaskResult(false, "failed", reason, stderrTail);
    }

    private void recordTask(String name, String kind, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("kind", kind);
        if (reason != null)
            m.put("reason", reason);
        record.add(m);
    }

    static String tail(byte[] stderr) {
        if (stderr == null || stderr.length == 0)
            return null;
        String s = new String(stderr, StandardCharsets.UTF_8);
        String[] lines = s.split("\n");
        int from = Math.max(0, lines.length - 20);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }

    private static String javaHome() {
        String home = System.getProperty("java.home");
        return home != null ? home : "java";
    }
}
