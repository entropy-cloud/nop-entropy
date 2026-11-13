package io.nop.code.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * W-04 unified git process bridge (plan 2565, facility F1; source anchor CODEINDEX-RT-001).
 *
 * <p><b>Watchdog BEFORE the read phase</b>: the single defect shape across all four legacy git
 * call sites was {@code waitFor(timeout)} placed AFTER the pipe-read phase — a blocked read
 * meant the timeout never fired (F5 shape). Here the watchdog ({@code destroyForcibly} after
 * {@code timeoutMillis}) is armed immediately after {@code start()}, THEN the calling thread
 * reads the pipe: destroy closes the stream, so the read returns no later than the timeout
 * point. {@code waitFor} reaps the process; the watchdog is cancelled on normal completion.</p>
 *
 * <p><b>Reaping finally</b>: on non-timeout exception paths (IOException /
 * InterruptedException) the process is still {@code destroyForcibly}ed before the exception
 * propagates — closes the process-object leak on error paths.</p>
 *
 * <p><b>Result object</b>: {@link Result} carries output + exitCode + timedOut; exception
 * translation stays at the call sites (site #1 maps timedOut to
 * ERR_CODE_REBUILD_NOT_GIT_REPO, site #4 maps timedOut to truncated=true without throwing).
 * Exception messages carry a dir+args summary only — zero credentials, zero environment
 * variables.</p>
 *
 * <p><b>extraEnv</b>: callers may pass additional environment variables (e.g. the
 * NOP_GIT_USERNAME/PASSWORD credential channel); the credential never appears in command-line
 * arguments or in this class's messages.</p>
 *
 * <p><b>Test seams</b>: instance {@code executable} (default "git", production zero diff) with
 * a package-visible static default and setter for the hang-stub injection in
 * TestGitProcessTimeout; call sites hold their own package-visible runner reference seams.</p>
 */
public class GitProcessRunner {

    /** shared daemon watchdog — one scheduler for all git bridges in the JVM */
    private static final ScheduledExecutorService WATCHDOG = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "git-process-watchdog");
        t.setDaemon(true);
        return t;
    });

    /** package-visible static default executable; production value "git" (zero diff) */
    static volatile String DEFAULT_EXECUTABLE = "git";

    /** package-visible setter for test hang-stub injection */
    static void setDefaultExecutable(String executable) {
        DEFAULT_EXECUTABLE = executable;
    }

    public static GitProcessRunner createDefault() {
        return new GitProcessRunner(DEFAULT_EXECUTABLE);
    }

    private final String executable;

    public GitProcessRunner(String executable) {
        this.executable = executable;
    }

    /** Result of a bridge run: full output capture or line-streamed (output null for runLines). */
    public static class Result {
        private final String output;
        private final Integer exitCode;
        private final boolean timedOut;

        public Result(String output, Integer exitCode, boolean timedOut) {
            this.output = output;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
        }

        public String getOutput() {
            return output;
        }

        /** null when the process did not exit (timed out and destroyed, or reaped on error) */
        public Integer getExitCode() {
            return exitCode;
        }

        public boolean isTimedOut() {
            return timedOut;
        }
    }

    /** line sink for the runLines streaming entry (parseGitDiff incremental parse contract) */
    public interface LineSink {
        void accept(String line);
    }

    /** full-capture entry: run the git command and return its merged output. */
    public Result run(Path dir, long timeoutMillis, List<String> args, Map<String, String> extraEnv) {
        Process process = start(dir, args, extraEnv);
        // fired flag (not Future.cancel's return value): a cancel landing while the watchdog
        // task is mid-run still returns true although destroy WILL happen — the flag is exact
        java.util.concurrent.atomic.AtomicBoolean watchdogFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            watchdogFired.set(true);
            process.destroyForcibly();
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        String output;
        Integer exitCode = null;
        boolean timedOut = false;
        try {
            output = readAll(process);
            if (watchdogFired.get()) {
                // hung process already destroyed by the watchdog — post-kill exit must
                // still classify as timedOut
                timedOut = true;
                if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    exitCode = process.exitValue();
                }
            } else if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
            } else {
                process.destroyForcibly();
                timedOut = true;
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // reaping finally: kill the process before propagating (no leaked process object)
            process.destroyForcibly();
            if (e instanceof InterruptedException) {
                throw new IllegalStateException("git process interrupted: " + summary(dir, args), e);
            }
            throw new IllegalStateException("git process io failure: " + summary(dir, args), e);
        } finally {
            watchdog.cancel(false);
        }
        return new Result(output, exitCode, timedOut);
    }

    /** line-streamed entry: incremental parse contract (readLine loop) under the same watchdog. */
    public Result runLines(Path dir, long timeoutMillis, List<String> args,
                           Map<String, String> extraEnv, LineSink sink) {
        Process process = start(dir, args, extraEnv);
        java.util.concurrent.atomic.AtomicBoolean watchdogFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            watchdogFired.set(true);
            process.destroyForcibly();
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        Integer exitCode = null;
        boolean timedOut = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            // watchdog armed BEFORE the read loop: destroy closes the stream, so readLine
            // returns (null) no later than the timeout point
            while ((line = reader.readLine()) != null) {
                sink.accept(line);
            }
            if (watchdogFired.get()) {
                timedOut = true;
                if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    exitCode = process.exitValue();
                }
            } else if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
            } else {
                process.destroyForcibly();
                timedOut = true;
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            process.destroyForcibly();
            if (e instanceof InterruptedException) {
                throw new IllegalStateException("git process interrupted: " + summary(dir, args), e);
            }
            throw new IllegalStateException("git process io failure: " + summary(dir, args), e);
        } finally {
            watchdog.cancel(false);
        }
        return new Result(null, exitCode, timedOut);
    }

    private Process start(Path dir, List<String> args, Map<String, String> extraEnv) {
        try {
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(command);
            if (dir != null) {
                pb.directory(dir.toFile());
            }
            if (extraEnv != null && !extraEnv.isEmpty()) {
                pb.environment().putAll(extraEnv);
            }
            pb.redirectErrorStream(true);
            return pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("git process start failure: " + summary(dir, args), e);
        }
    }

    private static String readAll(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /** dir+args summary for diagnostics — zero credentials, zero environment variables */
    private static String summary(Path dir, List<String> args) {
        return "dir=" + dir + " args=" + truncate(args);
    }

    private static List<String> truncate(List<String> args) {
        if (args == null) {
            return Collections.emptyList();
        }
        return args.size() <= 8 ? args : args.subList(0, 8);
    }
}
