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
     * Create a bare repository ({@code git init --bare}): HEAD/refs/objects
     * live at the top level, no working tree.
     */
    static void initBare(File dir, String defaultBranch) {
        io.nop.git.core.JGitRepositoryService.initBare(dir, defaultBranch);
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
     * Create a branch at HEAD without switching ({@code git branch <name>}).
     */
    void createBranch(String name);

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

    // ---- Wave 4 (design 04 terminal contract extension) ----

    /**
     * {@code git clone <from> <to>} for local paths (policy engine loaded
     * from the target parent for the "clone" operation).
     */
    static void cloneRepository(File from, File to) {
        io.nop.git.core.JGitRepositoryService.cloneRepository(from, to);
    }

    /**
     * Switch branches; create first when create is true ({@code checkout -b}).
     */
    void checkout(String branch, boolean create);

    /**
     * {@code git reset} with soft/mixed/hard semantics against the given rev.
     */
    void reset(GitResetMode mode, String rev);

    /**
     * {@code git clean}: remove (or list with dryRun) untracked paths.
     */
    List<String> clean(CleanOptions options);

    /**
     * {@code git restore <path>}: discard worktree changes of tracked paths.
     */
    void restore(String path);

    /**
     * {@code git rm}.
     */
    void rm(String path, boolean cached, boolean force);

    /**
     * {@code git mv}.
     */
    void mv(String oldPath, String newPath);

    /**
     * {@code git stash}: stash tracked changes (default message).
     *
     * @return the commit sha of the created stash, or null when there was
     *         nothing to stash
     */
    String stashPush();

    /**
     * {@code git stash list}: one line per stash entry.
     */
    List<String> stashList();

    /**
     * {@code git stash pop}: apply the latest stash and drop it.
     */
    void stashPop();

    /**
     * {@code git worktree list}.
     */
    List<NopGitWorktree> listWorktrees();

    /**
     * {@code git worktree add} (self-built metadata, design 05).
     */
    NopGitWorktree addWorktree(AddWorktreeOptions options);

    /**
     * {@code git worktree remove}.
     */
    void removeWorktree(String path, boolean force);

    /**
     * {@code git worktree prune}.
     *
     * @return number of pruned admin entries
     */
    int pruneWorktrees();

    /**
     * {@code git remote} names.
     */
    List<String> remoteList();

    /**
     * {@code git remote -v} lines: {@code <name>\t<url> (fetch)}` etc.
     */
    List<String> remoteListVerbose();

    /**
     * {@code git remote add}.
     */
    void remoteAdd(String name, String url);

    /**
     * {@code git remote set-url}.
     */
    void remoteSetUrl(String name, String url);

    /**
     * {@code git remote rm}.
     */
    void remoteRemove(String name);

    /**
     * {@code git push <remote> <refspec>} (policy: branch/remote dimensions).
     */
    void push(String remote, String refspec, boolean setUpstream);

    /**
     * {@code git fetch <remote>}.
     */
    void fetch(String remote);

    /**
     * {@code git pull <remote> <branch>} (fetch + fast-forward merge).
     */
    void pull(String remote, String branch);

    /**
     * {@code git merge <ref>} (fast-forward default; noFf forces a merge
     * commit). Conflict raises NopGitException.
     */
    void merge(String ref, boolean noFf);

    /**
     * {@code git rebase <upstream>} (simple non-interactive form).
     */
    void rebase(String upstream);

    /**
     * {@code git cherry-pick <commit>}.
     */
    io.nop.git.api.NopGitCommit cherryPick(String commit);

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
