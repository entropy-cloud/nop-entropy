package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;
import io.nop.ai.toolkit.fs.IToolFileSystem;

import java.nio.charset.StandardCharsets;

import static io.nop.ai.shell.NopAiShellErrors.ARG_DETAIL;
import static io.nop.ai.shell.NopAiShellErrors.ERR_AI_SHELL_INVALID_STATE;
import static io.nop.ai.shell.NopAiShellErrors.ERR_AI_SHELL_OUTPUT_CAP_EXCEEDED;

/**
 * plan 2557 C8-010 (ruling I): threshold chunked flush to the redirect target
 * file. The previous implementation buffered the whole redirected stream
 * unbounded and flushed it in one piece on close — a giant command output both
 * ballooned memory and was lost entirely if the process died before close.
 *
 * <p>Behavior:
 * <ul>
 * <li>Buffered text is flushed to disk whenever it reaches {@code
 * flushThresholdChars} (default 64K chars), so intermediate state is visible
 * in the target file before close.</li>
 * <li><b>Append-mode trap:</b> with the original {@code append=false}
 * (truncate) semantics, the FIRST flush must write with {@code append=false}
 * and every subsequent flush switches to {@code append=true} — otherwise each
 * chunked flush would erase the previous one.</li>
 * <li><b>Byte cap</b> (default 2 MiB, aligned with the bash precedent):
 * exceeding the cap FAILS the command loud with {@link
 * ERR_AI_SHELL_OUTPUT_CAP_EXCEEDED} — explicit error, never silent truncation.</li>
 * </ul>
 */
public class FileShellOutput implements IShellOutput {

    public static final int DEFAULT_FLUSH_THRESHOLD_CHARS = 64 * 1024;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 2L * 1024 * 1024;

    private final String filePath;
    private final IToolFileSystem fileSystem;
    private final boolean append;
    private final int flushThresholdChars;
    private final long maxTotalBytes;

    private final StringBuilder buffer = new StringBuilder();
    private long totalBytes = 0;
    private boolean flushedOnce = false;
    private volatile boolean closed = false;

    public FileShellOutput(String filePath, IToolFileSystem fileSystem) {
        this(filePath, fileSystem, false);
    }

    public FileShellOutput(String filePath, IToolFileSystem fileSystem, boolean append) {
        this(filePath, fileSystem, append, DEFAULT_FLUSH_THRESHOLD_CHARS, DEFAULT_MAX_TOTAL_BYTES);
    }

    FileShellOutput(String filePath, IToolFileSystem fileSystem, boolean append,
                    int flushThresholdChars, long maxTotalBytes) {
        this.filePath = filePath;
        this.fileSystem = fileSystem;
        this.append = append;
        this.flushThresholdChars = flushThresholdChars;
        this.maxTotalBytes = maxTotalBytes;
    }

    @Override
    public void write(ShellChunk chunk) {
        if (closed) throw new NopException(ERR_AI_SHELL_INVALID_STATE).param("detail", "output closed");
        if (chunk.isText()) {
            String text = chunk.asText();
            int chunkBytes = text.getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes + chunkBytes > maxTotalBytes) {
                // ruling I: over-cap = command fails loud, no silent truncation.
                // Flush what is already buffered so the on-disk partial output
                // reflects everything accepted before the failure.
                flush();
                throw new NopException(ERR_AI_SHELL_OUTPUT_CAP_EXCEEDED).param(ARG_DETAIL,
                        "path=" + filePath + ", capBytes=" + maxTotalBytes + ", rejectedChunkBytes=" + chunkBytes);
            }
            buffer.append(text);
            totalBytes += chunkBytes;
            if (buffer.length() >= flushThresholdChars) {
                flush();
            }
        }
    }

    @Override
    public void flush() {
        if (buffer.length() > 0) {
            String content = buffer.toString();
            buffer.setLength(0);
            // append trap: first flush preserves the caller's append choice
            // (false = truncate), subsequent flushes must append or they would
            // erase the previously flushed chunks.
            fileSystem.writeText(filePath, content, flushedOnce || append);
            flushedOnce = true;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            flush();
        }
    }

    @Override
    public IShellInput asInput() {
        return new FileShellInput(filePath, fileSystem);
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isAppend() {
        return append;
    }
}
