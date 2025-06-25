/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.impl;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.git.api.IGitRepository;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.ApplyCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.nop.git.GitErrors.ARG_NEW_COMMIT;
import static io.nop.git.GitErrors.ARG_OLD_COMMIT;
import static io.nop.git.GitErrors.ARG_PATH;
import static io.nop.git.GitErrors.ARG_REVISION;
import static io.nop.git.GitErrors.ERR_GIT_FILE_NOT_FOUND;
import static io.nop.git.GitErrors.ERR_GIT_INVALID_COMMIT_ID;
import static io.nop.git.GitErrors.ERR_GIT_NO_HEAD_COMMIT;

public class GitRepositoryImpl implements IGitRepository {
    static final Logger LOG = LoggerFactory.getLogger(GitRepositoryImpl.class);

    private final String remoteBaseUrl;
    private final File rootDir;
    private final Repository repository;
    private final CredentialsProvider credentialsProvider;
    private final String path;

    // 保持原有构造函数不变
    public GitRepositoryImpl(String remoteBaseUrl, File rootDir,
                             CredentialsProvider credentialsProvider) {
        this.remoteBaseUrl = remoteBaseUrl;
        this.rootDir = Guard.notNull(rootDir, "rootDir");
        this.repository = newRepository();
        this.credentialsProvider = credentialsProvider;
        this.path = FileHelper.getAbsolutePath(rootDir);
    }

    protected String getFullRemoteUrl(String path) {
        if (!path.endsWith(Constants.DOT_GIT)) {
            path += Constants.DOT_GIT;
        }
        if (remoteBaseUrl == null)
            return path;
        Guard.checkArgument(StringHelper.isCanonicalFilePath(path), "remotePath");
        return StringHelper.appendPath(remoteBaseUrl, path);
    }

    /* 实现简化后的接口方法 */

    @Override
    public boolean isInitialized() {
        File gitDir = getGitDir();
        return gitDir.isDirectory();
    }

    @Override
    public void init() {
        try {
            if (!rootDir.mkdirs()) {
                LOG.info("nop.git.make-dir-fail:path={}", path);
            }
            repository.create();
        } catch (Exception e) {
            throw NopEvalException.adapt(e);
        }
    }

    @Override
    public String getRepositoryPath() {
        return rootDir.getAbsolutePath();
    }

    @Override
    public void cloneRepository(String remoteUrl) {
        CloneCommand command = Git.cloneRepository()
                .setDirectory(rootDir)
                .setURI(getFullRemoteUrl(remoteUrl))
                .setCredentialsProvider(credentialsProvider);

        Git git = gitCall(command);
        IoHelper.safeCloseObject(git);
        LOG.info("nop.git.clone:path={},state={}", path, git.getRepository().getRepositoryState());
    }

    @Override
    public void push() {
        Git git = new Git(repository);
        PushCommand command = git.push()
                .setCredentialsProvider(credentialsProvider);

        Iterable<PushResult> result = gitCall(command);
        LOG.info("nop.git.push-result:path={},result={}", path, result);
    }

    @Override
    public void pull() {
        Git git = new Git(repository);
        PullCommand command = git.pull()
                .setCredentialsProvider(credentialsProvider);

        PullResult result = gitCall(command);
        LOG.info("nop.git.pull-result:path={},result={}", path, result);
    }

    @Override
    public String getLatestCommitHash() {
        try {
            try (RevWalk revWalk = new RevWalk(repository)) {
                Ref head = repository.findRef(Constants.HEAD);
                if (head == null || head.getObjectId() == null) {
                    throw new NopException(ERR_GIT_NO_HEAD_COMMIT)
                            .param(ARG_PATH, path);
                }
                RevCommit commit = revWalk.parseCommit(head.getObjectId());
                return commit.getName();
            }
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    @Override
    public boolean hasUncommittedChanges() {
        try {
            Git git = new Git(repository);
            Status status = git.status().call();
            return !status.isClean();
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    @Override
    public void add(String... paths) {
        Git git = new Git(repository);
        AddCommand command = git.add();
        if (paths != null) {
            for (String path : paths) {
                command.addFilepattern(path);
            }
        }
        gitCall(command);
    }

    @Override
    public CommitResult commit(String message, String author) {
        Git git = new Git(repository);
        CommitCommand command = git.commit()
                .setMessage(message)
                .setAuthor(author, null);

        RevCommit commit = gitCall(command);

        CommitResult result = new CommitResult();
        result.setCommitId(commit.getName());
        result.setShortMessage(commit.getShortMessage());
        result.setAuthor(author);
        result.setCommitTime(commit.getCommitTime() * 1000L); // 转换为毫秒

        LOG.info("nop.git.commit-result:path={},result={}", path, result);
        return result;
    }

    @Override
    public IResource getResource(String path) {
        ResourceHelper.checkValidRelativeName(path);
        return new FileResource(new File(rootDir, path));
    }

    @Override
    public String getFileContent(String path, String revision) {
        try {
            ObjectId commitId = repository.resolve(revision);
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                RevTree tree = commit.getTree();

                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree)) {
                    if (treeWalk == null) {
                        throw new NopException(ERR_GIT_FILE_NOT_FOUND)
                                .param(ARG_PATH, path)
                                .param(ARG_REVISION, revision);
                    }

                    ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    return new String(loader.getBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    @Override
    public List<ChangedFile> getChangedFilesBetweenCommits(String oldCommit, String newCommit) {
        try {
            Git git = new Git(repository);
            ObjectId oldId = repository.resolve(oldCommit);
            ObjectId newId = repository.resolve(newCommit);

            List<ChangedFile> result = new ArrayList<>();

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(repository);

                for (DiffEntry entry : diffFormatter.scan(oldId, newId)) {
                    ChangedFile changedFile = new ChangedFile();
                    changedFile.setPath(entry.getNewPath());
                    changedFile.setChangeType(entry.getChangeType().name());
                    result.add(changedFile);
                }
            }

            return result;
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    @Override
    public List<GitDiff> getWorkingTreeDiff() {
        try {
            Git git = new Git(repository);
            List<GitDiff> result = new ArrayList<>();

            try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                 DiffFormatter diffFormatter = new DiffFormatter(out)) {
                diffFormatter.setRepository(repository);

                // 比较工作区和暂存区
                ObjectReader reader = repository.newObjectReader();
                ObjectId headId = repository.resolve(Constants.HEAD);
                CanonicalTreeParser oldTree = new CanonicalTreeParser();
                if (headId != null) {
                    oldTree.reset(reader, new RevWalk(repository).parseTree(headId));
                }

                CanonicalTreeParser newTree = new CanonicalTreeParser();
                DirCacheIterator dirCacheIter = new DirCacheIterator(repository.readDirCache());
                newTree.reset(reader, dirCacheIter.getEntryObjectId());

                List<DiffEntry> entries = diffFormatter.scan(oldTree, newTree);

                for (DiffEntry entry : entries) {
                    diffFormatter.format(entry);
                    String diffText = out.toString(StandardCharsets.UTF_8);
                    out.reset();

                    GitDiff diff = new GitDiff();
                    diff.setChangeType(entry.getChangeType().name());
                    diff.setNewPath(entry.getNewPath());
                    diff.setOldPath(entry.getOldPath());
                    diff.setDiffContent(diffText);
                    result.add(diff);
                }
            }
            return result;
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    @Override
    public List<GitDiff> getCommitDiff(String oldCommit, String newCommit) {
        try {
            Git git = new Git(repository);
            List<GitDiff> result = new ArrayList<>();

            try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                 DiffFormatter diffFormatter = new DiffFormatter(out)) {
                diffFormatter.setRepository(repository);

                // 解析两个提交的树对象
                ObjectId oldId = repository.resolve(oldCommit + "^{tree}");
                ObjectId newId = repository.resolve(newCommit + "^{tree}");

                if (oldId == null || newId == null) {
                    throw new NopException(ERR_GIT_INVALID_COMMIT_ID)
                            .param(ARG_OLD_COMMIT, oldCommit)
                            .param(ARG_NEW_COMMIT, newCommit);
                }

                List<DiffEntry> entries = diffFormatter.scan(oldId, newId);

                for (DiffEntry entry : entries) {
                    diffFormatter.format(entry);
                    String diffText = out.toString(StandardCharsets.UTF_8);
                    out.reset();

                    GitDiff diff = new GitDiff();
                    diff.setChangeType(entry.getChangeType().name());
                    diff.setNewPath(entry.getNewPath());
                    diff.setOldPath(entry.getOldPath());
                    diff.setDiffContent(diffText);
                    result.add(diff);
                }
            }
            return result;
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    @Override
    public boolean applyDiff(GitDiff diff) {
        try (InputStream input = new ByteArrayInputStream(
                diff.getDiffContent().getBytes(StandardCharsets.UTF_8))) {

            Git git = new Git(repository);
            ApplyCommand command = git.apply()
                    .setPatch(input);

            gitCall(command);
            return true;
        } catch (Exception e) {
            LOG.error("nop.git.apply-diff-fail:path={},changeType={}",
                    diff.getNewPath(), diff.getChangeType(), e);
            return false;
        }
    }

    @Override
    public int applyDiffs(List<GitDiff> diffs) {
        int successCount = 0;
        for (GitDiff diff : diffs) {
            if (applyDiff(diff)) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public void close() {
        repository.close();
    }

    /* 原有辅助方法 */

    private File getGitDir() {
        return new File(rootDir, Constants.DOT_GIT);
    }

    private Repository newRepository() {
        try {
            return new FileRepositoryBuilder().setGitDir(getGitDir()).build();
        } catch (Exception e) {
            throw NopEvalException.adapt(e);
        }
    }

    protected <T> T gitCall(GitCommand<T> command) {
        try {
            return command.call();
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    protected RuntimeException handleError(Exception e) {
        return NopException.adapt(e);
    }
}