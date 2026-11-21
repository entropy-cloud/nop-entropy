package io.nop.ib.service;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * plan 2588: one on-disk log per job (design 09): stdout and stderr are tee'd
 * into {@code {repoRoot}/.nop-ib/jobs/{id}.log} with stream markers, so the
 * log file is the durable execution record while the payload keeps its exact
 * CLI {@code --json} contract. Directory + file are created lazily on the
 * first line (a lane-full rejection leaves no empty directory behind).
 */
final class JobLog {

    private final Path file;
    private Writer writer;

    JobLog(Path file) {
        this.file = file;
    }

    /** Appends one marked line; creates the directory and file on first use. */
    synchronized void line(String marker, String text) {
        try {
            if (writer == null) {
                Files.createDirectories(file.getParent());
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE);
            }
            writer.write(marker);
            writer.write(text);
            writer.write("\n");
            writer.flush();
        } catch (IOException ignored) {
            // best-effort: losing a log line must never fail the build job
        }
    }

    /** Appends a control line from anywhere (cancel record); never throws. */
    static void appendLine(Path file, String line) {
        if (file == null)
            return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // best-effort: losing a control line must not fail the cancel path
        }
    }

    synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // teardown
            }
            writer = null;
        }
    }
}
