package io.nop.ib.cli;

import io.nop.ib.json.JsonOut;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable run record (mvn-output equivalence for an AI-driven workflow): every
 * {@code ib compile/test/check} mirrors its human output into
 * {@code .nop-ib/logs/<cmd>-<timestamp>.log} and writes the machine-readable
 * {@code .nop-ib/last-run.json} on close. mvn's consumers are a terminal and
 * surefire-reports; nop-ib's consumers are AI sessions and post-hoc audits,
 * which must be able to read the full evidence (failure details, stderr tails,
 * slow classes, pollution events, compiler diagnostics) after the process is
 * gone.
 *
 * <p>Thread-safe: module reports arrive concurrently from the -pl pool.
 */
public final class RunLog implements AutoCloseable {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path logFile;
    private final Path stateFile;
    private final String cmd;
    private final LocalDateTime startedAt = LocalDateTime.now();
    private final List<Map<String, Object>> moduleEntries = new ArrayList<>();
    private final List<String> generalLines = new ArrayList<>();
    private int exitCode;
    private boolean closed;
    /** true when the log file could not be created: all writes become no-ops */
    private final boolean disabled;

    private RunLog(Path repoRoot, String cmd, boolean disabled) {
        this.cmd = cmd;
        this.disabled = disabled;
        this.logFile = repoRoot.resolve(".nop-ib").resolve("logs")
                .resolve(cmd + "-" + startedAt.format(TS) + ".log");
        this.stateFile = repoRoot.resolve(".nop-ib").resolve("last-run.json");
    }

    public static RunLog start(Path repoRoot, String cmd) {
        RunLog log = new RunLog(repoRoot, cmd, false);
        try {
            Files.createDirectories(log.logFile.getParent());
            Files.writeString(log.logFile,
                    "# nop-ib " + cmd + " log " + log.startedAt + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            // log persistence is best-effort telemetry; the run verdict must not
            // depend on it. A disabled log simply leaves nothing to read.
            return new RunLog(repoRoot, cmd, true);
        }
        return log;
    }

    /** a line that belongs to the run as a whole (usage errors, fallbacks...). */
    public synchronized void line(String line) {
        if (disabled)
            return;
        generalLines.add(line);
        append(line);
    }

    /** one module's outcome: the same human line the console shows, plus the
     *  structured entry that will land in last-run.json. */
    public synchronized void module(Map<String, Object> report, String humanLine) {
        if (disabled)
            return;
        moduleEntries.add(new LinkedHashMap<>(report));
        append(humanLine);
        Object detail = report.get("failedDetail");
        if (detail instanceof String s && !s.isBlank()) {
            for (String l : s.split("\n"))
                append("    " + l);
        }
    }

    public synchronized void exitCode(int code) {
        this.exitCode = code;
    }

    private void append(String line) {
        try {
            Files.writeString(logFile, line + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // best-effort: see start()
        }
    }

    /** writes last-run.json; prints nothing (the caller announces the path). */
    @Override
    public synchronized void close() {
        if (closed || disabled)
            return;
        closed = true;
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("cmd", cmd);
        doc.put("log", logFile.toString());
        doc.put("startedAt", startedAt.toString());
        doc.put("exitCode", exitCode);
        doc.put("modules", moduleEntries);
        if (!generalLines.isEmpty())
            doc.put("notes", generalLines);
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, JsonOut.write(doc), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best-effort: see start()
        }
    }

    public Path getLogFile() {
        return logFile;
    }

    static void announce(PrintWriter out, RunLog log) {
        if (log != null && !log.disabled)
            out.println("[nop-ib] log: " + log.getLogFile());
    }
}
