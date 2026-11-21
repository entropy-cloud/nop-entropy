package io.nop.git.core;

import io.nop.git.api.ChangeType;
import io.nop.git.api.DiffOptions;
import io.nop.git.api.GitConfigScope;
import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.LogOptions;
import io.nop.git.api.AddWorktreeOptions;
import io.nop.git.api.GitResetMode;
import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitDiffEntry;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitRef;
import io.nop.git.api.NopGitRename;
import io.nop.git.api.CleanOptions;
import io.nop.git.api.NopGitStatus;
import io.nop.git.api.NopGitWorktree;
import io.nop.git.policy.GitRequests;
import io.nop.git.policy.IGitOperationRequest;
import io.nop.git.policy.IGitPolicyEngine;
import io.nop.git.policy.GitPolicyMode;
import io.nop.git.policy.GitVerdict;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single JGit touch point of the module (design 01-architecture-baseline
 * section 1): every JGit call is converged in this adapter, all public
 * signatures use nop-git's own types, and no class outside {@code io.nop.git}
 * may import {@code org.eclipse.jgit}.
 */
public final class JGitRepositoryService implements IGitRepositoryService {

    /**
     * Context line count of unified diff hunks (native git default {@code -U3}).
     */
    private static final int CONTEXT = 3;

    /**
     * Abbreviation length for blob/commit SHAs (native git default for small
     * repositories).
     */
    private static final int ABBREV = 7;

    private final Repository repository;
    private final IGitPolicyEngine policy;
    private boolean closed;

    private JGitRepositoryService(Repository repository, IGitPolicyEngine policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public static JGitRepositoryService open(File workDir) {
        return open(workDir, io.nop.git.policy.DefaultGitPolicyEngine.load(
                Path.of(canonicalRoot(workDir))));
    }

    public static JGitRepositoryService open(File workDir, IGitPolicyEngine policy) {
        JdkBaseline.requireBaseline();
        RepositoryBuilder builder = new RepositoryBuilder();
        try {
            builder.findGitDir(workDir);
            if (builder.getGitDir() == null)
                throw new NopGitException("not a git repository: " + workDir.getAbsolutePath()
                        + " (no .git directory found)");
            return new JGitRepositoryService(builder.build(), policy);
        } catch (IOException e) {
            throw new NopGitException("cannot open git repository at "
                    + workDir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public static JGitRepositoryService init(File workDir, String defaultBranch) {
        return init(workDir, defaultBranch, io.nop.git.policy.DefaultGitPolicyEngine.load(
                Path.of(canonicalRoot(workDir))));
    }

    /**
     * Resolve the effective {@code init.defaultBranch} from the JGit config
     * chain (system, then user); {@code null} when unset anywhere (caller
     * falls back to git's built-in master). Exposed for the CLI init command,
     * which must not import JGit directly (ArchitecturePillarTest).
     */
    public static String defaultBranchFromConfig() {
        try {
            var reader = org.eclipse.jgit.util.SystemReader.getInstance();
            var systemConfig = reader.getSystemConfig();
            String branch = systemConfig == null ? null
                    : systemConfig.getString("init", null, "defaultBranch");
            if (branch == null)
                branch = reader.getUserConfig().getString("init", null, "defaultBranch");
            return branch;
        } catch (Exception e) {
            return null;
        }
    }

    public static void initBare(File dir, String defaultBranch) {
        JdkBaseline.requireBaseline();
        if (dir == null)
            throw new NopGitException("init directory must not be null");
        try (Git git = Git.init()
                .setDirectory(dir)
                .setBare(true)
                .setInitialBranch(defaultBranch)
                .call()) {
            git.getRepository().close();
        } catch (Exception e) {
            throw new NopGitException("cannot init bare repository at "
                    + dir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public static JGitRepositoryService init(File workDir, String defaultBranch,
                                             IGitPolicyEngine policy) {
        JdkBaseline.requireBaseline();
        if (workDir == null)
            throw new NopGitException("init directory must not be null");
        try {
            Git git = Git.init()
                    .setDirectory(workDir)
                    .setInitialBranch(defaultBranch)
                    .call();
            git.close();
            return open(workDir, policy);
        } catch (Exception e) {
            throw new NopGitException("cannot init git repository at "
                    + workDir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public NopGitStatus status() {
        return guarded(GitRequests.of("status", repoPath(), "status"), this::doStatus);
    }

    private NopGitStatus doStatus() {
        Git git = git();
        try {
            org.eclipse.jgit.api.Status st = git.status().call();
            // native long status reports the symbolic branch even for unborn
            // HEAD ("On branch master / No commits yet"); currentBranch()
            // keeps the null-on-unborn API contract
            String branch = repository.getBranch();

            // staged renames: HEAD tree vs index through the rename detector
            List<NopGitRename> renames = new ArrayList<>();
            Set<String> renamedOld = new HashSet<>();
            Set<String> renamedNew = new HashSet<>();
            if (headCommit() != null) {
                for (DiffEntry entry : detectRenames(headTree(), dirCacheIterator())) {
                    if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
                        renames.add(new NopGitRename(entry.getOldPath(), entry.getNewPath()));
                        renamedOld.add(entry.getOldPath());
                        renamedNew.add(entry.getNewPath());
                    }
                }
            }

            // hide rename halves from the add/remove lists, like native status
            List<String> added = new ArrayList<>(sorted(st.getAdded()));
            List<String> removed = new ArrayList<>(sorted(st.getRemoved()));
            List<String> changed = new ArrayList<>(sorted(st.getChanged()));
            added.removeAll(renamedNew);
            removed.removeAll(renamedOld);
            changed.removeAll(renamedNew);
            changed.removeAll(renamedOld);

            return new NopGitStatus(
                    branch,
                    added,
                    changed,
                    removed,
                    renames,
                    sorted(st.getModified()),
                    sorted(st.getMissing()),
                    collapseUntrackedFolders(sorted(st.getUntracked()), st.getUntrackedFolders()));
        } catch (Exception e) {
            throw wrap("git status failed", e);
        } finally {
            git.close();
        }
    }

    @Override
    public List<NopGitDiffEntry> diff(DiffOptions options) {
        return guarded(GitRequests.of("diff", repoPath(), "diff"), () -> doDiff(options));
    }

    private List<NopGitDiffEntry> doDiff(DiffOptions options) {
        try (TreeWalk walk = buildDiffWalk(options)) {
            List<DiffEntry> entries = filterUntracked(detectRenames(walk), options);
            List<NopGitDiffEntry> result = new ArrayList<>(entries.size());
            for (DiffEntry entry : entries) {
                result.add(toDiffEntry(entry));
            }
            return result;
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("git diff failed", e);
        }
    }

    @Override
    public List<String> unifiedDiff(DiffOptions options) {
        return guarded(GitRequests.of("diff", repoPath(), "diff"), () -> doUnifiedDiff(options));
    }

    private List<String> doUnifiedDiff(DiffOptions options) {
        try (TreeWalk walk = buildDiffWalk(options)) {
            List<DiffEntry> entries = filterUntracked(detectRenames(walk), options);
            List<String> out = new ArrayList<>();
            for (DiffEntry entry : entries) {
                appendUnifiedHeader(out, entry);
                appendHunks(out, entry);
            }
            return out;
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("git diff failed", e);
        }
    }

    @Override
    public List<NopGitCommit> log(LogOptions options) {
        return guarded(GitRequests.of("log", repoPath(), "log"), () -> doLog(options));
    }

    private List<NopGitCommit> doLog(LogOptions options) {
        Git git = git();
        try {
            String rev = options.rev() == null ? "HEAD" : options.rev();
            if (repository.resolve(rev) == null)
                return Collections.emptyList(); // unborn HEAD: git log prints nothing
            Iterable<RevCommit> commits = git.log().add(repository.resolve(rev))
                    .setMaxCount(options.maxCount() > 0 ? options.maxCount() : Integer.MAX_VALUE)
                    .call();
            List<NopGitCommit> result = new ArrayList<>();
            for (RevCommit commit : commits) {
                result.add(toCommit(commit));
            }
            return result;
        } catch (Exception e) {
            throw wrap("git log failed", e);
        } finally {
            git.close();
        }
    }

    @Override
    public String currentBranch() {
        return guarded(GitRequests.of("status", repoPath(), "symbolic-ref"), () -> doGetCurrentBranch());
    }

    private String doGetCurrentBranch() {
        if (headCommit() == null)
            return null; // unborn HEAD: no current branch per API contract
        try {
            return repository.getBranch();
        } catch (IOException e) {
            throw wrap("cannot determine current branch", e);
        }
    }

    @Override
    public String resolveRevision(String rev) {
        return guarded(GitRequests.of("rev-parse", repoPath(), "rev-parse " + rev),
                () -> doResolveRevision(rev));
    }

    private String doResolveRevision(String rev) {
        try {
            ObjectId id = repository.resolve(rev);
            return id == null ? null : id.name();
        } catch (IOException e) {
            throw wrap("cannot resolve revision " + rev, e);
        }
    }

    @Override
    public void createBranch(String name) {
        guarded(GitRequests.of("branch", repoPath(), "branch " + name), () -> {
            try (Git git = git()) {
                ObjectId head = repository.resolve(Constants.HEAD);
                if (head == null)
                    throw new io.nop.git.api.NopGitException(
                            "cannot create branch: repository has no commit yet");
                git.branchCreate().setName(name).setStartPoint(head.getName()).call();
            } catch (IOException | org.eclipse.jgit.api.errors.GitAPIException e) {
                throw wrap("branch create failed for '" + name + "'", e);
            }
            return null;
        });
    }

    @Override
    public List<NopGitRef> branchList() {
        return guarded(GitRequests.of("branch", repoPath(), "branch"), this::doBranchList);
    }

    private List<NopGitRef> doBranchList() {
        try {
            return mapRefs(repository.getRefDatabase()
                    .getRefsByPrefix(Constants.R_HEADS), false);
        } catch (IOException e) {
            throw wrap("git branch failed", e);
        }
    }

    @Override
    public List<NopGitRef> branchListAll() {
        return guarded(GitRequests.of("branch", repoPath(), "branch -a"), this::doBranchListAll);
    }

    private List<NopGitRef> doBranchListAll() {
        try {
            List<NopGitRef> all = new ArrayList<>(mapRefs(
                    repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS), false));
            all.addAll(mapRefs(
                    repository.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES), true));
            return all;
        } catch (IOException e) {
            throw wrap("git branch failed", e);
        }
    }

    @Override
    public String topLevel() {
        return guarded(GitRequests.of("rev-parse", repoPath(), "rev-parse --show-toplevel"),
                this::doTopLevel);
    }

    private String doTopLevel() {
        File workTree = repository.getWorkTree();
        try {
            return workTree.toPath().toRealPath().toString();
        } catch (IOException e) {
            return workTree.getAbsolutePath();
        }
    }

    @Override
    public String configGet(String key, GitConfigScope scope) {
        return guarded(GitRequests.of("config", repoPath(), "config --get " + key),
                () -> doGetConfig(key, scope));
    }

    private String doGetConfig(String key, GitConfigScope scope) {
        String[] parts = splitKey(key);
        try {
            // strict scope read: repository scope must not fall back to the
            // global config (native `git config --local user.name` semantics)
            StoredConfig config;
            if (scope == GitConfigScope.GLOBAL) {
                config = SystemReader.getInstance().getUserConfig();
                config.load();
            } else {
                java.io.File configFile = repository.getConfig() instanceof org.eclipse.jgit.storage.file.FileBasedConfig fbc
                        ? fbc.getFile()
                        : new java.io.File(repository.getDirectory(), "config");
                config = new org.eclipse.jgit.storage.file.FileBasedConfig(null, configFile,
                        org.eclipse.jgit.util.FS.DETECTED);
                config.load();
            }
            return config.getString(parts[0], parts[1], parts[2]);
        } catch (IOException | org.eclipse.jgit.errors.ConfigInvalidException e) {
            throw wrap("cannot read git config " + key, e);
        }
    }

    @Override
    public void configSet(String key, String value, GitConfigScope scope) {
        guarded(GitRequests.of("config", repoPath(), "config " + key + " " + value),
                () -> doSetConfig(key, value, scope));
    }

    private Void doSetConfig(String key, String value, GitConfigScope scope) {
        String[] parts = splitKey(key);
        try {
            StoredConfig config = scopeConfig(scope);
            config.load();
            config.setString(parts[0], parts[1], parts[2], value);
            config.save();
            return null;
        } catch (IOException | org.eclipse.jgit.errors.ConfigInvalidException e) {
            throw wrap("cannot write git config " + key, e);
        }
    }

    @Override
    public void add(String filepattern) {
        // path rules need the actual worktree paths the pattern matches
        List<String> paths = expandPathspec(filepattern);
        var builder = GitRequests.builder("add", repoPath(), "add " + filepattern);
        paths.forEach(builder::path);
        guarded(builder.build(), () -> doAdd(filepattern));
    }

    private Void doAdd(String filepattern) {
        Git git = git();
        try {
            git.add().addFilepattern(filepattern).call();
            return null;
        } catch (Exception e) {
            throw wrap("git add failed for pattern '" + filepattern + "'", e);
        } finally {
            git.close();
        }
    }

    @Override
    public NopGitCommit commit(String message, String authorName, String authorEmail) {
        return guarded(GitRequests.of("commit", repoPath(),
                "commit -m " + message), () -> doCommit(message, authorName, authorEmail));
    }

    private NopGitCommit doCommit(String message, String authorName, String authorEmail) {
        Git git = git();
        try {
            org.eclipse.jgit.api.CommitCommand cmd = git.commit().setMessage(message);
            if (authorName == null && authorEmail == null) {
                String configName = repository.getConfig().getString("user", null, "name");
                String configEmail = repository.getConfig().getString("user", null, "email");
                if (StringUtils.isEmptyOrNull(configName) || StringUtils.isEmptyOrNull(configEmail)) {
                    throw new NopGitException("commit identity is not configured: set user.name "
                            + "and user.email in git config, or pass authorName/authorEmail");
                }
            } else {
                if (StringUtils.isEmptyOrNull(authorName) || StringUtils.isEmptyOrNull(authorEmail))
                    throw new NopGitException("authorName and authorEmail must both be non-empty "
                            + "when an explicit commit identity is given");
                PersonIdent author = new PersonIdent(authorName, authorEmail);
                cmd.setAuthor(author).setCommitter(author);
            }
            RevCommit commit = cmd.call();
            return toCommit(commit);
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("git commit failed", e);
        } finally {
            git.close();
        }
    }

    // ------------------------------------------------------------------
    // Wave 4: P2/P3 commands + worktree (design 05; guarded like all ops)
    // ------------------------------------------------------------------

    public static void cloneRepository(File from, File to) {
        IGitPolicyEngine engine = io.nop.git.policy.DefaultGitPolicyEngine.load(
                Path.of(canonicalRoot(to.getParentFile() == null ? to : to.getParentFile())));
        var request = GitRequests.builder("clone",
                canonicalRoot(to.getParentFile() == null ? to : to.getParentFile()),
                "clone " + from + " " + to).build();
        runGuarded(engine, request, () -> {
            Wave4Ops.cloneRepository(from, to);
            return null;
        });
    }

    static <T> T runGuarded(IGitPolicyEngine engine, IGitOperationRequest request,
                            java.util.function.Supplier<T> action) {
        if (engine == null || !engine.isActive())
            return action.get();
        GitVerdict verdict = engine.evaluate(request);
        boolean observe = engine.mode() == GitPolicyMode.OBSERVE;
        if (!observe && verdict.effect() == GitVerdict.Effect.DENY) {
            engine.audit(request, verdict, "denied");
            throw new NopGitException("policy denied: " + verdict.reason());
        }
        if (!observe && verdict.effect() == GitVerdict.Effect.REVIEW
                && !engine.resolveReview(request, verdict)) {
            engine.audit(request, verdict, "review-denied");
            throw new NopGitException("policy denied: review not approved: " + verdict.reason());
        }
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            engine.audit(request, verdict, "error");
            throw e;
        }
        engine.audit(request, verdict, "ok");
        return result;
    }

    @Override
    public void checkout(String branch, boolean create) {
        guarded(GitRequests.of("checkout", repoPath(),
                "checkout " + (create ? "-b " : "") + branch),
                () -> {
                    Wave4Ops.checkout(repository, branch, create);
                    return null;
                });
    }

    @Override
    public void reset(GitResetMode mode, String rev) {
        var builder = GitRequests.builder("reset", repoPath(),
                "reset --" + mode.name().toLowerCase() + " " + (rev == null ? "" : rev));
        builder.canonicalFlag("--" + mode.name().toLowerCase());
        guarded(builder.build(), () -> {
            Wave4Ops.reset(repository, mode, rev);
            return null;
        });
    }

    @Override
    public List<String> clean(CleanOptions options) {
        var builder = GitRequests.builder("clean", repoPath(), "clean");
        if (options.force())
            builder.canonicalFlag("--force");
        if (options.directories())
            builder.canonicalFlag("--directories");
        if (options.dryRun())
            builder.canonicalFlag("--dry-run");
        return guarded(builder.build(), () -> Wave4Ops.clean(repository, options));
    }

    @Override
    public void restore(String path) {
        var builder = GitRequests.builder("restore", repoPath(), "restore " + path);
        builder.path(path);
        guarded(builder.build(), () -> {
            Wave4Ops.restore(repository, path);
            return null;
        });
    }

    @Override
    public void rm(String path, boolean cached, boolean force) {
        var builder = GitRequests.builder("rm", repoPath(), "rm " + path);
        builder.path(path);
        if (cached)
            builder.canonicalFlag("--cached");
        if (force)
            builder.canonicalFlag("--force");
        guarded(builder.build(), () -> {
            Wave4Ops.rm(repository, path, cached, force);
            return null;
        });
    }

    @Override
    public void mv(String oldPath, String newPath) {
        var builder = GitRequests.builder("mv", repoPath(), "mv " + oldPath + " " + newPath);
        builder.path(oldPath);
        builder.path(newPath);
        guarded(builder.build(), () -> {
            Wave4Ops.mv(repository, oldPath, newPath);
            return null;
        });
    }

    @Override
    public String stashPush() {
        return guarded(GitRequests.of("stash", repoPath(), "stash push"),
                () -> Wave4Ops.stashPush(repository));
    }

    @Override
    public List<String> stashList() {
        return guarded(GitRequests.of("stash", repoPath(), "stash list"),
                () -> Wave4Ops.stashList(repository));
    }

    @Override
    public void stashPop() {
        guarded(GitRequests.of("stash", repoPath(), "stash pop"), () -> {
            Wave4Ops.stashPop(repository);
            return null;
        });
    }

    @Override
    public List<NopGitWorktree> listWorktrees() {
        return guarded(GitRequests.of("worktree-list", repoPath(), "worktree list"),
                () -> WorktreeSupport.list(repository));
    }

    @Override
    public NopGitWorktree addWorktree(AddWorktreeOptions options) {
        return guarded(GitRequests.of("worktree-add", repoPath(),
                        "worktree add " + options.path()),
                () -> WorktreeSupport.add(repository, options));
    }

    @Override
    public void removeWorktree(String path, boolean force) {
        var builder = GitRequests.builder("worktree-remove", repoPath(),
                "worktree remove " + path);
        builder.path(path);
        if (force)
            builder.canonicalFlag("--force");
        guarded(builder.build(), () -> {
            WorktreeSupport.remove(repository, path, force);
            return null;
        });
    }

    @Override
    public int pruneWorktrees() {
        return guarded(GitRequests.of("worktree-prune", repoPath(), "worktree prune"),
                () -> WorktreeSupport.prune(repository));
    }

    @Override
    public List<String> remoteList() {
        return guarded(GitRequests.of("remote", repoPath(), "remote"),
                () -> Wave4Ops.remoteList(repository));
    }

    @Override
    public List<String> remoteListVerbose() {
        return guarded(GitRequests.of("remote", repoPath(), "remote -v"),
                () -> Wave4Ops.remoteListVerbose(repository));
    }

    @Override
    public void remoteAdd(String name, String url) {
        guarded(GitRequests.builder("remote", repoPath(), "remote add " + name + " " + url)
                        .remote(name, url).build(),
                () -> {
                    Wave4Ops.remoteAdd(repository, name, url);
                    return null;
                });
    }

    @Override
    public void remoteSetUrl(String name, String url) {
        guarded(GitRequests.builder("remote", repoPath(), "remote set-url " + name + " " + url)
                        .remote(name, url).build(),
                () -> {
                    Wave4Ops.remoteSetUrl(repository, name, url);
                    return null;
                });
    }

    @Override
    public void remoteRemove(String name) {
        guarded(GitRequests.builder("remote", repoPath(), "remote rm " + name)
                        .remote(name, null).build(),
                () -> {
                    Wave4Ops.remoteRemove(repository, name);
                    return null;
                });
    }

    @Override
    public void push(String remote, String refspec, boolean setUpstream) {
        var builder = GitRequests.builder("push", repoPath(),
                "push " + remote + " " + (refspec == null ? "" : refspec));
        builder.remote(remote, repository.getConfig().getString("remote", remote, "url"));
        if (refspec != null && !refspec.isEmpty()) {
            // force-semantic normalization single point (plan 2582): the
            // refspec path handles "+", wildcards and refs/heads/ prefixes
            builder.refspec(refspec);
        } else {
            // no refspec ("push -u origin"): native pushes the current
            // branch, so the guard evaluates against it (plan 2582: legal
            // operation, must not bypass the protected-branch guard)
            builder.targetBranch(Wave4Ops.currentBranchOrNull(repository));
        }
        guarded(builder.build(), () -> {
            Wave4Ops.push(repository, remote, refspec, setUpstream);
            return null;
        });
    }

    @Override
    public void fetch(String remote) {
        guarded(GitRequests.builder("fetch", repoPath(), "fetch " + remote)
                        .remote(remote, repository.getConfig()
                                .getString("remote", remote, "url")).build(),
                () -> {
                    Wave4Ops.fetch(repository, remote);
                    return null;
                });
    }

    @Override
    public void pull(String remote, String branch) {
        guarded(GitRequests.builder("pull", repoPath(), "pull " + remote + " " + branch)
                        .remote(remote, repository.getConfig()
                                .getString("remote", remote, "url"))
                        .targetBranch(branch).build(),
                () -> {
                    Wave4Ops.pull(repository, remote, branch);
                    return null;
                });
    }

    @Override
    public void merge(String ref, boolean noFf) {
        var builder = GitRequests.builder("merge", repoPath(), "merge " + ref);
        if (noFf)
            builder.canonicalFlag("--no-ff");
        builder.targetBranch(ref);
        guarded(builder.build(), () -> {
            Wave4Ops.merge(repository, ref, noFf);
            return null;
        });
    }

    @Override
    public void rebase(String upstream) {
        var builder = GitRequests.builder("rebase", repoPath(), "rebase " + upstream);
        builder.targetBranch(upstream);
        guarded(builder.build(), () -> {
            Wave4Ops.rebase(repository, upstream);
            return null;
        });
    }

    @Override
    public NopGitCommit cherryPick(String commit) {
        return guarded(GitRequests.of("cherry-pick", repoPath(), "cherry-pick " + commit),
                () -> Wave4Ops.cherryPick(repository, commit, c -> {
                }));
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            repository.close();
        }
    }

    // ------------------------------------------------------------------
    // generic helpers
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // policy interception (design 03 section 6, point 2)
    // ------------------------------------------------------------------

    /**
     * Evaluate the policy for one facade operation, execute the action when
     * allowed/review-approved, and write a single audit line with the final
     * outcome. Inactive engines pass straight through.
     */
    private <T> T guarded(IGitOperationRequest request, java.util.function.Supplier<T> action) {
        if (policy == null || !policy.isActive()) {
            return action.get();
        }
        GitVerdict verdict = policy.evaluate(request);
        boolean observe = policy.mode() == GitPolicyMode.OBSERVE;
        // observe mode never blocks (design 03 section 5); the audit record
        // carries the hypothetical enforce verdict instead
        if (!observe && verdict.effect() == GitVerdict.Effect.DENY) {
            policy.audit(request, verdict, "denied");
            throw new NopGitException("policy denied: " + verdict.reason());
        }
        if (!observe && verdict.effect() == GitVerdict.Effect.REVIEW
                && !policy.resolveReview(request, verdict)) {
            policy.audit(request, verdict, "review-denied");
            throw new NopGitException("policy denied: review not approved: " + verdict.reason());
        }
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            policy.audit(request, verdict, "error");
            throw e;
        }
        policy.audit(request, verdict, "ok");
        return result;
    }

    private String repoPath() {
        try {
            return repository.getWorkTree().toPath().toRealPath().toString();
        } catch (IOException e) {
            return repository.getWorkTree().getAbsolutePath();
        }
    }

    private static String canonicalRoot(File workDir) {
        try {
            return workDir.toPath().toRealPath().toString();
        } catch (IOException e) {
            return workDir.getAbsolutePath();
        }
    }

    /**
     * Expand an add/rm pathspec into the actual worktree paths it matches
     * (plan 2578 pinned semantics: {@code .} / {@code -A} cover the whole
     * tree; a directory prefix covers everything below it).
     */
    private List<String> expandPathspec(String filepattern) {
        List<String> matches = new ArrayList<>();
        try {
            File root = repository.getWorkTree();
            Path rootPath = root.toPath();
            String prefix = filepattern == null || filepattern.equals(".")
                    || filepattern.equals("-A") || filepattern.equals("--all")
                    ? "" : filepattern.replaceAll("/+$", "") + "/";
            try (var stream = Files.walk(rootPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> !p.startsWith(rootPath.resolve(".git")))
                        .forEach(p -> {
                            String rel = rootPath.relativize(p).toString().replace(File.separatorChar, '/');
                            if (prefix.isEmpty() || rel.startsWith(prefix))
                                matches.add(rel);
                            else if (prefix.endsWith("/"))
                                // also match a directory argument itself being a file
                                if (rel.startsWith(prefix.substring(0, prefix.length() - 1))
                                        && rel.contains("/"))
                                    matches.add(rel);
                        });
            }
        } catch (IOException e) {
            throw wrap("cannot expand pathspec '" + filepattern + "'", e);
        }
        return matches;
    }

    private Git git() {
        if (closed)
            throw new IllegalStateException("repository service is already closed");
        return new Git(repository);
    }

    private NopGitException wrap(String message, Exception cause) {
        return new NopGitException(message + ": " + cause.getMessage(), cause);
    }

    private ObjectId headCommit() {
        try {
            return repository.resolve(Constants.HEAD);
        } catch (IOException e) {
            throw wrap("cannot resolve HEAD", e);
        }
    }

    private static List<String> sorted(Set<String> paths) {
        List<String> list = new ArrayList<>(paths);
        Collections.sort(list);
        return list;
    }

    private static NopGitCommit toCommit(RevCommit commit) {
        return new NopGitCommit(
                commit.getName(),
                commit.abbreviate(ABBREV).name(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(),
                OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(commit.getCommitTime()),
                        ZoneOffset.ofTotalSeconds(commit.getAuthorIdent()
                                .getTimeZoneOffset() * 60)),
                commit.getShortMessage(),
                commit.getFullMessage(),
                parentIds(commit));
    }

    private static List<String> parentIds(RevCommit commit) {
        List<String> ids = new ArrayList<>(commit.getParentCount());
        for (RevCommit parent : commit.getParents()) {
            ids.add(parent.getName());
        }
        return ids;
    }

    private static ChangeType toChangeType(DiffEntry.ChangeType type) {
        return switch (type) {
            case ADD -> ChangeType.ADDED;
            case MODIFY -> ChangeType.MODIFIED;
            case DELETE -> ChangeType.DELETED;
            case RENAME -> ChangeType.RENAMED;
            case COPY -> ChangeType.COPIED;
        };
    }

    // ------------------------------------------------------------------
    // refs / config
    // ------------------------------------------------------------------

    private List<NopGitRef> mapRefs(List<Ref> refs, boolean remote) {
        List<NopGitRef> result = new ArrayList<>();
        for (Ref ref : refs) {
            result.add(new NopGitRef(
                    ref.getName(),
                    Repository.shortenRefName(ref.getName()),
                    ref.getObjectId() == null ? null : ref.getObjectId().name(),
                    ref.getName().startsWith(Constants.R_HEADS),
                    remote,
                    ref.isSymbolic() ? ref.getTarget().getName() : null));
        }
        return result;
    }

    private StoredConfig scopeConfig(GitConfigScope scope) {
        try {
            return scope == GitConfigScope.GLOBAL
                    ? SystemReader.getInstance().getUserConfig()
                    : repository.getConfig();
        } catch (IOException | org.eclipse.jgit.errors.ConfigInvalidException e) {
            throw wrap("cannot open git user config", e);
        }
    }

    private static String[] splitKey(String key) {
        int first = key.indexOf('.');
        if (first <= 0 || first == key.length() - 1)
            throw new NopGitException("invalid git config key: " + key
                    + " (expected <section>.<name> or <section>.<subsection>.<name>)");
        String rest = key.substring(first + 1);
        int second = rest.indexOf('.');
        if (second < 0)
            return new String[]{key.substring(0, first), null, rest};
        return new String[]{key.substring(0, first), rest.substring(0, second),
                rest.substring(second + 1)};
    }

    // ------------------------------------------------------------------
    // diff plumbing
    // ------------------------------------------------------------------

    private TreeWalk buildDiffWalk(DiffOptions options) throws IOException {
        TreeWalk walk = new TreeWalk(repository);
        walk.setRecursive(true);
        if (options.cached()) {
            ObjectId head = headCommit();
            if (head == null) {
                walk.addTree(new EmptyTreeIterator());
            } else {
                walk.addTree(treeParser(headTree()));
            }
            walk.addTree(new DirCacheIterator(readDirCache()));
        } else if (options.rev() != null) {
            ObjectId rev = repository.resolve(options.rev());
            if (rev == null)
                throw new NopGitException("unknown revision: " + options.rev());
            walk.addTree(treeParser(treeOfCommit(rev)));
            walk.addTree(new FileTreeIterator(repository));
        } else {
            walk.addTree(new DirCacheIterator(readDirCache()));
            walk.addTree(new FileTreeIterator(repository));
        }
        return walk;
    }

    private CanonicalTreeParser treeParser(ObjectId treeId) throws IOException {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader(), treeId);
        return parser;
    }

    private ObjectReader reader() {
        return repository.newObjectReader();
    }

    /**
     * Run rename detection over a diff walk, mirroring git's default
     * {@code diff.renames=true} behavior.
     */
    private List<DiffEntry> detectRenames(TreeWalk walk) throws IOException {
        List<DiffEntry> entries = DiffEntry.scan(walk);
        if (entries.isEmpty())
            return entries;
        RenameDetector detector = new RenameDetector(repository);
        detector.addAll(entries);
        return detector.compute();
    }

    private List<DiffEntry> detectRenames(ObjectId oldTree,
                                          AbstractTreeIterator newSide) throws IOException {
        TreeWalk walk = new TreeWalk(repository);
        walk.setRecursive(true);
        walk.addTree(treeParser(oldTree));
        walk.addTree(newSide);
        return detectRenames(walk);
    }

    /**
     * Native {@code git diff} never reports untracked files: in worktree and
     * rev modes an ADD entry whose path is absent from the index is an
     * untracked file and must be dropped (cached mode compares against the
     * index only, so its ADD entries are staged and kept).
     */
    private List<DiffEntry> filterUntracked(List<DiffEntry> entries, DiffOptions options)
            throws IOException {
        if (options.cached())
            return entries;
        DirCache index = readDirCache();
        List<DiffEntry> kept = new ArrayList<>(entries.size());
        for (DiffEntry entry : entries) {
            if (entry.getChangeType() == DiffEntry.ChangeType.ADD
                    && index.getEntry(entry.getNewPath()) == null)
                continue;
            kept.add(entry);
        }
        return kept;
    }

    private DirCacheIterator dirCacheIterator() {
        return new DirCacheIterator(readDirCache());
    }

    private DirCache readDirCache() {
        try {
            return repository.readDirCache();
        } catch (IOException e) {
            throw wrap("cannot read index", e);
        }
    }

    private NopGitDiffEntry toDiffEntry(DiffEntry entry) throws IOException {
        boolean binary = isBinary(entry);
        int[] counts = countChanges(entry);
        return new NopGitDiffEntry(
                toChangeType(entry.getChangeType()),
                entry.getOldPath(),
                entry.getNewPath(),
                binary ? 0 : counts[0],
                binary ? 0 : counts[1],
                abbrev(entry.getOldId()),
                abbrev(entry.getNewId()),
                modeString(entry.getNewMode() != FileMode.MISSING
                        ? entry.getNewMode() : entry.getOldMode()),
                binary);
    }

    private ObjectId oldBlobId(DiffEntry entry) {
        ObjectId id = entry.getOldId() == null ? null : entry.getOldId().toObjectId();
        return (id == null || ObjectId.zeroId().equals(id)) ? null : id;
    }

    private ObjectId newBlobId(DiffEntry entry) {
        ObjectId id = entry.getNewId() == null ? null : entry.getNewId().toObjectId();
        return (id == null || ObjectId.zeroId().equals(id)) ? null : id;
    }

    private String abbrev(org.eclipse.jgit.lib.AbbreviatedObjectId id) {
        if (id == null)
            return "0000000";
        ObjectId full = id.toObjectId();
        return full == null ? "0000000" : abbrev(full);
    }

    private String abbrev(ObjectId id) {
        if (id == null || ObjectId.zeroId().equals(id))
            return "0000000";
        try (ObjectReader reader = reader()) {
            return reader.abbreviate(id, ABBREV).name();
        } catch (IOException e) {
            throw wrap("cannot abbreviate object " + id.name(), e);
        }
    }

    private static String modeString(FileMode mode) {
        return Integer.toOctalString(mode.getBits());
    }

    private boolean isBinary(DiffEntry entry) {
        return loadText(entry, true) == null || loadText(entry, false) == null;
    }

    /**
     * Count insertions/deletions with git diffstat semantics: a replaced block
     * counts on both sides. Binary content reports 0/0.
     */
    private int[] countChanges(DiffEntry entry) {
        if (entry.getChangeType() == DiffEntry.ChangeType.ADD
                || entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
            // add/delete: the only existing side is new for ADD, old for DELETE
            boolean add = entry.getChangeType() == DiffEntry.ChangeType.ADD;
            return add
                    ? new int[]{lineCount(entry, false), 0}
                    : new int[]{0, lineCount(entry, true)};
        }
        RawText oldText = loadText(entry, true);
        RawText newText = loadText(entry, false);
        if (oldText == null || newText == null)
            return new int[]{0, 0}; // binary or unreadable side
        EditList edits = diffEdits(oldText, newText);
        int insertions = 0;
        int deletions = 0;
        for (Edit edit : edits) {
            if (edit.getType() != Edit.Type.DELETE)
                insertions += edit.getEndB() - edit.getBeginB();
            if (edit.getType() != Edit.Type.INSERT)
                deletions += edit.getEndA() - edit.getBeginA();
        }
        return new int[]{insertions, deletions};
    }

    private int lineCount(DiffEntry entry, boolean oldSide) {
        RawText text = loadText(entry, oldSide);
        return text == null ? 0 : text.size();
    }

    private static EditList diffEdits(RawText oldText, RawText newText) {
        return new HistogramDiff().diff(RawTextComparator.DEFAULT, oldText, newText);
    }

    /**
     * Load the diff entry side as {@link RawText}, or null when the content is
     * binary or unavailable (missing worktree file is treated as empty).
     */
    private RawText loadText(DiffEntry entry, boolean oldSide) {
        try {
            if (oldSide) {
                ObjectId id = oldBlobId(entry);
                if (id == null)
                    return new RawText(new byte[0]);
                ObjectLoader loader = repository.open(id);
                return loader.isLarge() ? null : asText(loader.getBytes());
            }
            ObjectId newId = newBlobId(entry);
            if (newId != null && repository.getObjectDatabase().has(newId)) {
                ObjectLoader loader = repository.open(newId);
                return loader.isLarge() ? null : asText(loader.getBytes());
            }
            // new side is a worktree file not in the object database
            File file = new File(repository.getWorkTree(), entry.getNewPath());
            if (!file.isFile())
                return new RawText(new byte[0]);
            byte[] bytes = Files.readAllBytes(file.toPath());
            return asText(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    private static RawText asText(byte[] bytes) {
        return RawText.isBinary(bytes) ? null : new RawText(bytes);
    }

    private ObjectId headTree() {
        ObjectId head = headCommit();
        if (head == null)
            throw new NopGitException("cannot read HEAD tree: repository has no commit yet");
        return treeOfCommit(head);
    }

    private ObjectId treeOfCommit(ObjectId commitId) {
        try {
            RevCommit commit = repository.parseCommit(commitId);
            return commit.getTree().getId();
        } catch (IOException e) {
            throw wrap("cannot read tree of commit " + commitId.name(), e);
        }
    }

    // ------------------------------------------------------------------
    // unified diff text (native git pure-text format)
    // ------------------------------------------------------------------

    private void appendUnifiedHeader(List<String> out, DiffEntry entry) throws IOException {
        // native git uses the surviving path on both sides for add/delete
        String headerOldPath = entry.getChangeType() == DiffEntry.ChangeType.ADD
                ? entry.getNewPath() : entry.getOldPath();
        String headerNewPath = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                ? entry.getOldPath() : entry.getNewPath();
        out.add("diff --git a/" + headerOldPath + " b/" + headerNewPath);
        switch (entry.getChangeType()) {
            case ADD -> {
                out.add("new file mode " + modeString(entry.getNewMode()));
                out.add("index " + abbrev(entry.getOldId()) + ".." + abbrev(entry.getNewId()));
            }
            case DELETE -> {
                out.add("deleted file mode " + modeString(entry.getOldMode()));
                out.add("index " + abbrev(entry.getOldId()) + ".." + abbrev(entry.getNewId()));
            }
            case RENAME -> {
                out.add("similarity index " + renameSimilarityPercent(entry) + "%");
                out.add("rename from " + entry.getOldPath());
                out.add("rename to " + entry.getNewPath());
                // pure rename: no index line and no hunks, like native git
                if (!sameBlob(oldBlobId(entry), newBlobId(entry))) {
                    out.add("index " + abbrev(entry.getOldId()) + ".." + abbrev(entry.getNewId())
                            + (entry.getOldMode() == entry.getNewMode()
                            ? " " + modeString(entry.getNewMode()) : ""));
                }
            }
            default -> out.add("index " + abbrev(entry.getOldId()) + ".." + abbrev(entry.getNewId())
                    + " " + modeString(entry.getNewMode()));
        }

        if (entry.getChangeType() == DiffEntry.ChangeType.RENAME
                && sameBlob(oldBlobId(entry), newBlobId(entry)))
            return; // pure rename: no content section

        if (isBinary(entry)) {
            out.add("Binary files " + (entry.getChangeType() == DiffEntry.ChangeType.ADD
                    ? "/dev/null" : "a/" + entry.getOldPath()) + " and "
                    + (entry.getChangeType() == DiffEntry.ChangeType.DELETE
                    ? "/dev/null" : "b/" + entry.getNewPath()) + " differ");
            return;
        }

        out.add("--- " + (entry.getChangeType() == DiffEntry.ChangeType.ADD
                ? "/dev/null" : "a/" + entry.getOldPath()));
        out.add("+++ " + (entry.getChangeType() == DiffEntry.ChangeType.DELETE
                ? "/dev/null" : "b/" + entry.getNewPath()));
    }

    private static boolean sameBlob(ObjectId a, ObjectId b) {
        return a != null && a.equals(b);
    }

    private String renameSimilarityPercent(DiffEntry entry) {
        int score = entry.getScore();
        if (score <= 0)
            return "100";
        return String.valueOf(Math.min(100, score));
    }

    private void appendHunks(List<String> out, DiffEntry entry) throws IOException {
        if (entry.getChangeType() == DiffEntry.ChangeType.RENAME
                && sameBlob(oldBlobId(entry), newBlobId(entry)))
            return; // pure rename

        RawText oldText = loadText(entry, true);
        RawText newText = loadText(entry, false);
        if (oldText == null || newText == null)
            return; // binary already handled in the header

        EditList edits = diffEdits(oldText, newText);
        if (edits.isEmpty())
            return;

        int a = 0; // next unemitted line of oldText
        for (int i = 0; i < edits.size(); i++) {
            Edit firstEdit = edits.get(i);
            Edit hunkEdit = firstEdit;

            // merge following edits into this hunk while their context
            // regions touch or overlap (native git rule)
            while (i + 1 < edits.size()
                    && edits.get(i + 1).getBeginA() - hunkEdit.getEndA() <= 2 * CONTEXT) {
                hunkEdit = edits.get(++i);
            }

            int ctxStartA = Math.max(0, firstEdit.getBeginA() - CONTEXT);
            int ctxStartB = Math.max(0, firstEdit.getBeginB() - CONTEXT);
            int ctxEndA = Math.min(oldText.size(), hunkEdit.getEndA() + CONTEXT);
            int ctxEndB = Math.min(newText.size(), hunkEdit.getEndB() + CONTEXT);

            out.add(hunkHeader(ctxStartA, ctxEndA, ctxStartB, ctxEndB));

            // leading context (shared prefix of both files)
            for (int line = a; line < ctxStartA; line++) {
                out.add(" " + oldText.getString(line));
            }

            int aCur = ctxStartA;
            for (int j = edits.indexOf(firstEdit); j <= i; j++) {
                Edit e = edits.get(j);
                for (int line = aCur; line < e.getBeginA(); line++) {
                    out.add(" " + oldText.getString(line));
                }
                for (int line = e.getBeginA(); line < e.getEndA(); line++) {
                    out.add("-" + oldText.getString(line));
                }
                for (int line = e.getBeginB(); line < e.getEndB(); line++) {
                    out.add("+" + newText.getString(line));
                }
                aCur = e.getEndA();
            }
            // trailing context
            for (int line = aCur; line < ctxEndA; line++) {
                out.add(" " + oldText.getString(line));
            }

            appendNoNewlineMarkers(out, oldText, newText, ctxEndA, ctxEndB);
            a = ctxEndA;
        }
    }

    private void appendNoNewlineMarkers(List<String> out, RawText oldText, RawText newText,
                                        int ctxEndA, int ctxEndB) {
        if (ctxEndA == oldText.size() && oldText.size() > 0 && oldText.isMissingNewlineAtEnd())
            out.add("\\ No newline at end of file");
        if (ctxEndB == newText.size() && newText.size() > 0 && newText.isMissingNewlineAtEnd())
            out.add("\\ No newline at end of file");
    }

    /**
     * Native git hunk header: 1-based starts; the ",count" part is omitted
     * when the count is exactly 1; empty ranges report the position before
     * the first line (0 when a file gains its first content).
     */
    private static String hunkHeader(int startA, int endA, int startB, int endB) {
        int countA = endA - startA;
        int countB = endB - startB;
        int lineA = countA == 0 ? startA : startA + 1;
        int lineB = countB == 0 ? startB : startB + 1;
        return "@@ -" + lineA + (countA == 1 ? "" : "," + countA)
                + " +" + lineB + (countB == 1 ? "" : "," + countB) + " @@";
    }

    /**
     * Collapse untracked directories: when every file under a folder is
     * untracked, native status reports the folder with a trailing slash
     * instead of the individual files.
     */
    private static List<String> collapseUntrackedFolders(List<String> untrackedFiles,
                                                         Set<String> untrackedFolders) {
        List<String> result = new ArrayList<>();
        for (String folder : sorted(untrackedFolders)) {
            result.add(folder + "/");
        }
        for (String file : untrackedFiles) {
            boolean covered = false;
            for (String folder : untrackedFolders) {
                if (file.startsWith(folder + "/")) {
                    covered = true;
                    break;
                }
            }
            if (!covered)
                result.add(file);
        }
        Collections.sort(result);
        return result;
    }
}
