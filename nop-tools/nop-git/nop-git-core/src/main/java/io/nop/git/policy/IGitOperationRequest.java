package io.nop.git.policy;

import java.util.List;

/**
 * Normalized description of one Git operation, the evaluation input of
 * {@link IGitPolicyEngine} (design 01-architecture-baseline section 3).
 * All collections are already canonical: flags normalized per the design 03
 * alias table, paths canonicalized (symlink/.. resolution) before matching.
 */
public interface IGitOperationRequest {

    /**
     * Git sub-command name ({@code status}, {@code push}, ...).
     */
    String operation();

    /**
     * Normalized flags (canonical long forms only).
     */
    List<String> flags();

    /**
     * Canonical paths the operation touches (pathspec already expanded for
     * add/rm per design 03 section 3 adjudication).
     */
    List<String> paths();

    /**
     * Target branch (refspec target for push, branch name for branch -d/-D),
     * or null.
     */
    String targetBranch();

    /**
     * Remote name, or null.
     */
    String remote();

    /**
     * Remote URL, or null.
     */
    String remoteUrl();

    /**
     * Canonical repository working tree path.
     */
    String repoPath();

    /**
     * The raw command line as invoked (CLI argv joined; API: method name +
     * arguments), for audit purposes.
     */
    String rawCommand();
}
