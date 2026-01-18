package io.nop.ai.shell.executor;

public enum PipeType {
    NONE,
    PIPE,
    AND,
    OR,
    OUTPUT_REDIRECT,
    OUTPUT_APPEND,
    ERROR_REDIRECT
}
