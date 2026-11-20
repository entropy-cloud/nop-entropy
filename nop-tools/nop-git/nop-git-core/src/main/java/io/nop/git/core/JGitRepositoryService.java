package io.nop.git.core;

import io.nop.git.api.ChangeType;
import io.nop.git.api.DiffOptions;
import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.LogOptions;
import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitDiffEntry;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitRef;
import io.nop.git.api.NopGitStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
     * The well-known empty tree object id, used to diff against unborn HEAD.
     */
    private static final String EMPTY_TREE_SHA = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";

    private final Repository repository;
    private boolean closed;

    private JGitRepositoryService(Repository repository) {
        this.repository = repository;
    }

    public static JGitRepositoryService open(File workDir) {
        JdkBaseline.requireBaseline();
        RepositoryBuilder builder = new RepositoryBuilder();
        try {
            builder.findGitDir(workDir);
            if (builder.getGitDir() == null)
                throw new NopGitException("not a git repository: " + workDir.getAbsolutePath()
                        + " (no .git directory found)");
            return new JGitRepositoryService(builder.build());
        } catch (IOException e) {
            throw new NopGitException("cannot open git repository at "
                    + workDir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public static JGitRepositoryService init(File workDir, String defaultBranch) {
        JdkBaseline.requireBaseline();
        if (workDir == null)
            throw new NopGitException("init directory must not be null");
        try {
            Git git = Git.init()
                    .setDirectory(workDir)
                    .setInitialBranch(defaultBranch)
                    .call();
            git.close();
            return open(workDir);
        } catch (Exception e) {
            throw new NopGitException("cannot init git repository at "
                    + workDir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public NopGitStatus status() {
        Git git = git();
        try {
            org.eclipse.jgit.api.Status st = git.status().call();
            String branch = headCommit() == null ? null : repository.getBranch();
            return new NopGitStatus(
                    branch,
                    sorted(st.getAdded()),
                    sorted(st.getChanged()),
                    sorted(st.getRemoved()),
                    sorted(st.getModified()),
                    sorted(st.getMissing()),
                    sorted(st.getUntracked()));
        } catch (Exception e) {
            throw wrap("git status failed", e);
        }
    }

    @Override
    public List<NopGitDiffEntry> diff(DiffOptions options) {
        Git git = git();
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.setRecursive(true);
            if (options.cached()) {
                ObjectId head = headCommit();
                if (head == null) {
                    walk.addTree(new EmptyTreeIterator());
                } else {
                    walk.addTree(new CanonicalTreeParser(null, repository.newObjectReader(),
                            headTree()));
                }
                walk.addTree(new DirCacheIterator(readDirCache()));
            } else if (options.rev() != null) {
                ObjectId rev = repository.resolve(options.rev());
                if (rev == null)
                    throw new NopGitException("unknown revision: " + options.rev());
                walk.addTree(new CanonicalTreeParser(null, repository.newObjectReader(),
                        treeOfCommit(rev)));
                walk.addTree(new FileTreeIterator(repository));
            } else {
                walk.addTree(new DirCacheIterator(readDirCache()));
                walk.addTree(new FileTreeIterator(repository));
            }

            List<DiffEntry> entries = DiffEntry.scan(walk);
            List<NopGitDiffEntry> result = new ArrayList<>(entries.size());
            for (DiffEntry entry : entries) {
                int[] counts = countChanges(entry);
                result.add(new NopGitDiffEntry(
                        toChangeType(entry.getChangeType()),
                        entry.getOldPath(),
                        entry.getNewPath(),
                        counts[0],
                        counts[1]));
            }
            return result;
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw wrap("git diff failed", e);
        } finally {
            git.close();
        }
    }

    @Override
    public List<NopGitCommit> log(LogOptions options) {
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
        try {
            ObjectId id = repository.resolve(rev);
            return id == null ? null : id.name();
        } catch (IOException e) {
            throw wrap("cannot resolve revision " + rev, e);
        }
    }

    @Override
    public List<NopGitRef> branchList() {
        Git git = git();
        try {
            List<NopGitRef> result = new ArrayList<>();
            for (Ref ref : git.branchList().call()) {
                result.add(new NopGitRef(
                        ref.getName(),
                        Repository.shortenRefName(ref.getName()),
                        ref.getObjectId() == null ? null : ref.getObjectId().name(),
                        ref.getName().startsWith(Constants.R_HEADS)));
            }
            return result;
        } catch (Exception e) {
            throw wrap("git branch failed", e);
        } finally {
            git.close();
        }
    }

    @Override
    public void add(String filepattern) {
        Git git = git();
        try {
            git.add().addFilepattern(filepattern).call();
        } catch (Exception e) {
            throw wrap("git add failed for pattern '" + filepattern + "'", e);
        } finally {
            git.close();
        }
    }

    @Override
    public NopGitCommit commit(String message, String authorName, String authorEmail) {
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
                cmd.setAuthor(new PersonIdent(authorName, authorEmail));
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

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            repository.close();
        }
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
                commit.abbreviate(7).name(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(),
                Instant.ofEpochSecond(commit.getCommitTime()),
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

    private DirCache readDirCache() {
        try {
            return repository.readDirCache();
        } catch (IOException e) {
            throw wrap("cannot read index", e);
        }
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
        EditList edits = new HistogramDiff().diff(RawTextComparator.DEFAULT, oldText, newText);
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

    /**
     * Load the diff entry side as {@link RawText}, or null when the content is
     * binary or unavailable (missing worktree file is treated as empty).
     */
    private RawText loadText(DiffEntry entry, boolean oldSide) {
        try {
            if (oldSide) {
                ObjectId id = entry.getOldId() == null ? null : entry.getOldId().toObjectId();
                if (id == null || ObjectId.zeroId().equals(id))
                    return new RawText(new byte[0]);
                ObjectLoader loader = repository.open(id);
                return loader.isLarge() ? null : asText(loader.getBytes());
            }
            ObjectId newId = entry.getNewId() == null ? null : entry.getNewId().toObjectId();
            if (newId != null && !ObjectId.zeroId().equals(newId)) {
                try {
                    if (repository.getObjectDatabase().has(newId)) {
                        ObjectLoader loader = repository.open(newId);
                        return loader.isLarge() ? null : asText(loader.getBytes());
                    }
                } catch (IOException ignored) {
                    // fall through to the worktree file below
                }
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
}
