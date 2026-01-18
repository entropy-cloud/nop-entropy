/*
 * Copyright (c) 2025, Entropy Cloud
 *
 * Licensed to the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.ai.shell.executor;

import java.io.*;

/**
 * Context for managing input/output streams in a pipeline.
 * Handles pipe chaining, stream redirection, and data passing between commands.
 */
public class PipeOutputContext {

    private InputStream pipeInput;
    private OutputStream pipeOutput;
    private OutputStream pipeError;
    private final ByteArrayOutputStream stdoutBuffer;
    private final ByteArrayOutputStream stderrBuffer;

    private OutputStream currentStdout;
    private OutputStream currentStderr;

    public PipeOutputContext() {
        this.stdoutBuffer = new ByteArrayOutputStream();
        this.stderrBuffer = new ByteArrayOutputStream();
        this.pipeInput = new ByteArrayInputStream(new byte[0]);
        this.currentStdout = stdoutBuffer;
        this.currentStderr = stderrBuffer;
    }

    /**
     * Redirect stdout to a file (and also capture in buffer).
     *
     * @param file: file to redirect to
     * @param append: true to append, false to overwrite
     * @throws IOException if file operation fails
     */
    public void redirectStdout(java.nio.file.Path file, boolean append) throws IOException {
        closeCurrentStdout();
        OutputStream fos = new FileOutputStream(file.toFile(), append);
        this.currentStdout = new TeeOutputStream(fos, stdoutBuffer);
    }

    /**
     * Redirect stderr to a file (and also capture in buffer).
     *
     * @param file: file to redirect to
     * @param append: true to append, false to overwrite
     * @throws IOException if file operation fails
     */
    public void redirectStderr(java.nio.file.Path file, boolean append) throws IOException {
        closeCurrentStderr();
        OutputStream fos = new FileOutputStream(file.toFile(), append);
        this.currentStderr = new TeeOutputStream(fos, stderrBuffer);
    }

    /**
     * Redirect stderr to stdout (2>&1).
     *
     * @throws IOException if stream operation fails
     */
    public void redirectStderrToStdout() throws IOException {
        closeCurrentStderr();
        this.currentStderr = new TeeOutputStream(this.currentStdout, this.stderrBuffer);
    }

    /**
     * Redirect stdout to stderr (1>&2).
     *
     * @throws IOException if stream operation fails
     */
    public void redirectStdoutToStderr() throws IOException {
        closeCurrentStdout();
        this.currentStdout = new TeeOutputStream(this.currentStderr, this.stdoutBuffer);
    }

    /**
     * Merge stdout and stderr to same file (&>file).
     *
     * @param file: file to redirect both streams to
     * @param append: true to append, false to overwrite
     * @throws IOException if file operation fails
     */
    public void redirectAllToFile(java.nio.file.Path file, boolean append) throws IOException {
        closeCurrentStdout();
        closeCurrentStderr();
        OutputStream fos = new FileOutputStream(file.toFile(), append);
        this.currentStdout = new TeeOutputStream(fos, this.stdoutBuffer);
        this.currentStderr = new TeeOutputStream(fos, this.stderrBuffer);
    }

    /**
     * Prepare next command's input by converting current stdout to input stream.
     * This is called between commands in a pipe chain.
     */
    public void prepareNextCommandInput() {
        byte[] output = stdoutBuffer.toByteArray();
        if (output.length == 0) {
            return;
        }
        this.pipeInput = new ByteArrayInputStream(output);
        stdoutBuffer.reset();
    }

    /**
     * Determine if execution should continue based on pipe type and exit code.
     *
     * @param pipeType: type of pipe
     * @param exitCode: exit code of the previous command
     * @return true if execution should continue, false otherwise
     */
    public boolean shouldContinue(PipeType pipeType, int exitCode) {
        switch (pipeType) {
            case PIPE:
            case STDOUT_REDIRECT:
            case STDOUT_APPEND:
            case STDOUT_REDIRECT_FD:
            case STDOUT_APPEND_FD:
            case STDERR_REDIRECT:
            case STDERR_APPEND:
            case STDERR_TO_STDOUT:
            case STDOUT_TO_STDERR:
            case MERGE_REDIRECT:
            case MERGE_APPEND:
                return true;
            case AND:
                return exitCode == 0;
            case OR:
                return exitCode != 0;
            default:
                return true;
        }
    }

    /**
     * Get input stream for current command (from previous command's stdout).
     *
     * @return input stream
     */
    public InputStream getPipeInput() {
        return pipeInput;
    }

    /**
     * Get output stream for current command's stdout.
     *
     * @return output stream
     */
    public OutputStream getPipeOutput() {
        return currentStdout;
    }

    /**
     * Get output stream for current command's stderr.
     *
     * @return error output stream
     */
    public OutputStream getPipeError() {
        return currentStderr;
    }

    /**
     * Get accumulated stdout content.
     *
     * @return stdout content as string
     */
    public String getStdout() {
        flushAll();
        return stdoutBuffer.toString();
    }

    /**
     * Get accumulated stderr content.
     *
     * @return stderr content as string
     */
    public String getStderr() {
        flushAll();
        return stderrBuffer.toString();
    }

    /**
     * Flush all output streams.
     */
    public void flushAll() {
        try {
            if (currentStdout != null) {
                currentStdout.flush();
            }
            if (currentStderr != null && currentStderr != currentStdout) {
                currentStderr.flush();
            }
        } catch (IOException e) {
            // Ignore flush errors
        }
    }

    /**
     * Close all output streams.
     */
    public void close() throws IOException {
        closeCurrentStdout();
        closeCurrentStderr();
        stdoutBuffer.reset();
        stderrBuffer.reset();
    }

    private void closeCurrentStdout() throws IOException {
        if (currentStdout != null && currentStdout != stdoutBuffer) {
            currentStdout.flush();
            currentStdout.close();
        }
    }

    private void closeCurrentStderr() throws IOException {
        if (currentStderr != null && currentStderr != stderrBuffer && currentStderr != currentStdout) {
            currentStderr.flush();
            currentStderr.close();
        }
    }
}
