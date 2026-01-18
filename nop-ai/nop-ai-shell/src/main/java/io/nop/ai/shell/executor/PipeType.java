package io.nop.ai.shell.executor;

/**
 * Pipeline and redirection types supported by the shell.
 * Supports standard pipes, conditional operators, and stream redirection.
 */
public enum PipeType {
    /** No pipe or redirection */
    NONE,
    
    /** Standard pipe: cmd1 | cmd2 */
    PIPE,
    
    /** AND operator: cmd1 && cmd2 (execute cmd2 only if cmd1 succeeds) */
    AND,
    
    /** OR operator: cmd1 || cmd2 (execute cmd2 only if cmd1 fails) */
    OR,
    
    /** Redirect stdout to file: cmd > file */
    STDOUT_REDIRECT,
    
    /** Append stdout to file: cmd >> file */
    STDOUT_APPEND,
    
    /** Redirect stdout with fd: cmd 1> file */
    STDOUT_REDIRECT_FD,
    
    /** Append stdout with fd: cmd 1>> file */
    STDOUT_APPEND_FD,
    
    /** Redirect stderr to file: cmd 2> file */
    STDERR_REDIRECT,
    
    /** Append stderr to file: cmd 2>> file */
    STDERR_APPEND,
    
    /** Redirect stderr to stdout: cmd 2>&1 */
    STDERR_TO_STDOUT,
    
    /** Redirect stdout to stderr: cmd 1>&2 */
    STDOUT_TO_STDERR,
    
    /** Merge stdout and stderr to file: cmd &> file */
    MERGE_REDIRECT,
    
    /** Merge stdout and stderr to file with append: cmd &>> file */
    MERGE_APPEND
}
