package io.nop.ib.compile;

import io.nop.ib.NopIbException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cold-path delegation: runs real Maven for routes the fast path cannot own
 * (AOP/codegen modules, stale model). Wave 11: mvnq is no longer invoked —
 * ib owns concurrency in-process. Maven location: system property
 * {@code nop.ib.maven} > project mvnw > mvn from PATH.
 */
public final class MavenFallback {
    public static final int EXIT_LOCK_TIMEOUT = 75;

    private final Path repoRoot;
    private final Duration timeout;

    public Duration getTimeout() {
        return timeout;
    }

    public MavenFallback(Path repoRoot) {
        // 2026-11-20 governance: 60min default let a lock-starved delegation hold a
        // service lane unobservably for an hour (nop-rg-vector E2E observation).
        // 10min default, overridable via nop.ib.maven.timeout (minutes).
        this(repoRoot, Duration.ofMinutes(
                Long.getLong("nop.ib.maven.timeout", 10)));
    }

    public MavenFallback(Path repoRoot, Duration timeout) {
        this.repoRoot = repoRoot;
        this.timeout = timeout;
    }

    /**
     * wave 11 item 2: list form used by the {@code mvn} passthrough. Args are
     * whitespace-joined into the same delegate path — fine for goals, flags
     * and {@code -pl :module}; values containing spaces are not a mvn CLI case.
     */
    public int delegate(List<String> mavenArgs, Appendable out, Appendable err) {
        return delegate(String.join(" ", mavenArgs), out, err);
    }

    public int delegate(String mavenArgs, Appendable out, Appendable err) {
        // wave 11 owner adjudication: the mvnq wrapper (and its build.lock) is
        // no longer invoked — ib controls concurrency in-process (design 07 §三).
        // Maven location: explicit system property nop.ib.maven wins, then the
        // project's mvnw wrapper, then plain mvn from PATH (defect 24 pin).
        Path mvnw = repoRoot.resolve("mvnw");
        List<String> cmd = new ArrayList<>();
        String configured = System.getProperty("nop.ib.maven");
        if (configured != null && !configured.isBlank()) {
            cmd.add(configured);
        } else if (Files.isRegularFile(mvnw)) {
            // external project with its own wrapper
            cmd.add("bash");
            cmd.add(mvnw.toAbsolutePath().toString());
        } else {
            // external project without a wrapper (flink-agents observation,
            // defect 24): the old code invoked repoRoot/mvnw regardless and the
            // delegation degraded to a bash "No such file or directory" line with
            // zero test summaries. Plain mvn from PATH honors the same project pom.
            cmd.add("mvn");
        }
        for (String arg : mavenArgs.split("\\s+"))
            if (!arg.isBlank())
                cmd.add(arg);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(timeout.toSeconds() + 60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                err.append("maven delegation timed out\n");
                return 1;
            }
            return process.exitValue();
        } catch (IOException e) {
            throw new NopIbException("failed to launch maven delegation: " + mavenArgs, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopIbException("interrupted during maven delegation");
        }
    }

    /** -pl form that stays below package phases (design 01 §15.1: 回退分档). */
    public int compileModules(List<String> modules, Appendable out, Appendable err) {
        return delegate("-pl " + plSelectors(modules)
                + " -am compile test-compile -q", out, err);
    }

    /** -pl 选择器：artifactId 须带 ':' 前缀（Maven reactor 选择器 :artifactId）——裸名会被
     *  解析为相对路径项目而报 "requested required projects ... do not exist"
     *  （2026-11-20 nop-rg-vector 回退实测暴露；depsUnresolved 路径同样受益） */
    public static String plSelectors(List<String> modules) {
        return modules.stream()
                .map(m -> m.startsWith(":") ? m : ":" + m)
                .reduce((a, b) -> a + "," + b).orElse("");
    }
}
