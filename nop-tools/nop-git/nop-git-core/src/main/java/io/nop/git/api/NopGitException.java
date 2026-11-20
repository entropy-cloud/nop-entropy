package io.nop.git.api;

/**
 * Module-level exception for the nop-git API facade. All error messages are in
 * English per platform convention. The NopException + ErrorCode adapter is
 * deferred to the Wave 6 IoC integration (plan 2576: nop-git-core has zero
 * repository-internal dependencies to keep the JDK 21 runtime baseline real).
 */
public class NopGitException extends RuntimeException {
    static final long serialVersionUID = -1L;

    public NopGitException(String message) {
        super(message);
    }

    public NopGitException(String message, Throwable cause) {
        super(message, cause);
    }
}
