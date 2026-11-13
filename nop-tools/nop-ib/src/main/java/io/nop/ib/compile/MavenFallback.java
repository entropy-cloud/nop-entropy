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
 * Cold-path delegation (design 05 §三/§7.3): always through the worktree's mvnq
 * wrapper so concurrency governance and the worktree local repo are respected.
 * Delegation itself takes the build lock via MvnqLock (the same file nop-ib's
 * {@link io.nop.ib.lock.BuildLock} uses), so lock discipline is one protocol.
 */
public final class MavenFallback {
    public static final int EXIT_LOCK_TIMEOUT = 75;

    private final Path repoRoot;
    private final Duration timeout;

    public MavenFallback(Path repoRoot) {
        this(repoRoot, Duration.ofMinutes(60));
    }

    public MavenFallback(Path repoRoot, Duration timeout) {
        this.repoRoot = repoRoot;
        this.timeout = timeout;
    }

    public int delegate(String mavenArgs, Appendable out, Appendable err) {
        Path mvnq = repoRoot.resolve("ai-dev/tools/mvnq");
        Path mvnw = repoRoot.resolve("mvnw");
        List<String> cmd = new ArrayList<>();
        cmd.add("bash");
        if (Files.isRegularFile(mvnq)) {
            cmd.add(mvnq.toAbsolutePath().toString());
            cmd.add("--lock-timeout=" + timeout.toSeconds());
        } else {
            cmd.add(mvnw.toAbsolutePath().toString());
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
        return delegate("-pl " + String.join(",", modules)
                + " -am compile test-compile -q", out, err);
    }
}
