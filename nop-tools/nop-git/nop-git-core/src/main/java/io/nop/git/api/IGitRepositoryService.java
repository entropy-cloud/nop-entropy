package io.nop.git.api;

import java.io.File;
import java.util.List;

/**
 * Semantic Git operation facade for in-platform Java integration
 * (design 04-api-integration). All signatures use nop-git's own types only;
 * JGit never leaks through this interface. The policy-engine interception
 * point is added in Wave 3; this Wave 1 subset is the declared true subset of
 * the design 04 contract (checkout/reset/clean/worktree/push extend it in
 * later waves).
 *
 * <p>Lifecycle: one instance per open repository, must be closed
 * (AutoCloseable); concurrent writers must serialize externally.
 */
public interface IGitRepositoryService extends AutoCloseable {

    /**
     * Open the repository that contains (or is) the given working directory.
     *
     * @throws NopGitException if no .git directory is found above workDir, or
     *                         the JDK runtime feature level is below 21
     */
    static IGitRepositoryService open(File workDir) {
        return io.nop.git.core.JGitRepositoryService.open(workDir);
    }

    /**
     * Open with an explicit policy engine; every public method consults it
     * before executing (design 03 section 6 interception point 2). The
     * engine instance should be shared with the CLI to guarantee a single
     * configuration source.
     */
    static IGitRepositoryService open(File workDir, io.nop.git.policy.IGitPolicyEngine policy) {
        return io.nop.git.core.JGitRepositoryService.open(workDir, policy);
    }

    /**
     * Create a new repository at workDir. The default branch follows
     * {@code init.defaultBranch} when configured, else {@code master}.
     *
     * @throws NopGitException if the directory cannot be initialized
     */
    static IGitRepositoryService init(File workDir) {
        return io.nop.git.core.JGitRepositoryService.init(workDir, null);
    }

    static IGitRepositoryService init(File workDir, io.nop.git.policy.IGitPolicyEngine policy) {
        return io.nop.git.core.JGitRepositoryService.init(workDir, null, policy);
    }

    /**
     * Create a new repository at workDir with an explicit default branch.
     */
    static IGitRepositoryService init(File workDir, String defaultBranch) {
        return io.nop.git.core.JGitRepositoryService.init(workDir, defaultBranch);
    }

    static IGitRepositoryService init(File workDir, String defaultBranch,
                                      io.nop.git.policy.IGitPolicyEngine policy) {
        return io.nop.git.core.JGitRepositoryService.init(workDir, defaultBranch, policy);
    }

    /**
     * Working tree / index status.
     */
    NopGitStatus status();

    /**
     * File-level diff; see {@link DiffOptions} for the supported invocation
     * shapes (cached / rev-vs-worktree / index-vs-worktree).
     */
    List<NopGitDiffEntry> diff(DiffOptions options);

    /**
     * Commit history starting at options.rev. Empty list when HEAD is unborn.
     */
    List<NopGitCommit> log(LogOptions options);

    /**
     * Current branch name, or null when the repository has no commit yet.
     */
    String currentBranch();

    /**
     * Resolve a revision expression ({@code HEAD}, {@code HEAD~1}, a SHA, a
     * branch name...). Returns the full object id, or null when it does not
     * resolve (unborn HEAD included).
     */
    String resolveRevision(String rev);

    /**
     * Local branch refs ({@code refs/heads/*}).
     */
    List<NopGitRef> branchList();

    /**
     * All refs: local branches plus remote-tracking refs
     * ({@code refs/remotes/*}), symbolic refs included
     * ({@code branch --all} data source).
     */
    List<NopGitRef> branchListAll();

    /**
     * Absolute (real) path of the working tree root
     * ({@code rev-parse --show-toplevel}).
     */
    String topLevel();

    /**
     * Read a config value (key like {@code user.name}) from the given scope.
     *
     * @return the value, or null when the key is not set
     */
    String configGet(String key, GitConfigScope scope);

    /**
     * Write a config value to the given scope ({@code git config <key> <value>}).
     */
    void configSet(String key, String value, GitConfigScope scope);

    /**
     * Unified diff text lines, byte-compatible with native git
     * ({@code git diff} pure text output; headers, hunks and the
     * {@code \ No newline at end of file} marker included). Empty when there
     * is no change.
     */
    List<String> unifiedDiff(DiffOptions options);

    /**
     * Stage paths matching the file pattern ({@code git add <filepattern>}).
     */
    void add(String filepattern);

    /**
     * Commit staged changes. When authorName is null the identity is read
     * from the git config chain (repository then user level); a missing
     * identity fails the commit.
     *
     * @return the created commit
     */
    NopGitCommit commit(String message, String authorName, String authorEmail);

    /**
     * Close the underlying repository. The instance must not be used
     * afterwards.
     */
    @Override
    void close();
}
