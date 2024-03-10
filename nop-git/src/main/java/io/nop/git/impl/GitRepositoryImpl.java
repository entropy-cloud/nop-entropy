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
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.git.api.IGitRepository;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class GitRepositoryImpl implements IGitRepository {
    static final Logger LOG = LoggerFactory.getLogger(GitRepositoryImpl.class);

    private final GitServerConfig config;
    private final String remoteUrl;
    private final File rootDir;

    private final String path;

    private final Repository repository;

    private final CredentialsProvider credentialsProvider;

    public GitRepositoryImpl(GitServerConfig config, String remoteUrl,
                             File rootDir, String path,
                             CredentialsProvider credentialsProvider) {
        this.config = Guard.notNull(config, "git server config");
        this.remoteUrl = remoteUrl;
        this.rootDir = Guard.notNull(rootDir, "rootDir");
        this.repository = newRepository();
        this.path = path;
        this.credentialsProvider = credentialsProvider;
    }

    public File getGitDir() {
        return new File(rootDir, Constants.DOT_GIT);
    }

    @Override
    public boolean isInitialized() {
        File gitDir = getGitDir();
        return gitDir.isDirectory();
    }

    @Override
    public void reset(boolean hard) {
        Git git = new Git(repository);
        ResetCommand command = git.reset();
        if (hard) {
            command.setMode(ResetCommand.ResetType.HARD);
        }

        Ref ref = gitCall(command);
        LOG.debug("nop.git.reset-result:path={},dir={},ref={}", path, rootDir, ref);
    }

    protected <T> T gitCall(GitCommand<T> command) {
        try {
            return command.call();
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    @Override
    public void close() {
        repository.close();
    }

    protected Repository newRepository() {
        try {
            Repository repository = new FileRepositoryBuilder().setGitDir(getGitDir()).build();
            return repository;
        } catch (Exception e) {
            throw NopEvalException.adapt(e);
        }
    }

    @Override
    public void create() {
        try {
            boolean b = rootDir.mkdirs();
            if (!b)
                LOG.info("nop.git.make-dir-fail:path={}", path);
            repository.create();
        } catch (Exception e) {
            throw NopEvalException.adapt(e);
        }
    }

    @Override
    public void cloneFromRemote() {
        CloneCommand command = Git.cloneRepository().setDirectory(rootDir)
                .setURI(remoteUrl);
        command.setCredentialsProvider(credentialsProvider);

        Git git = gitCall(command);
        IoHelper.safeCloseObject(git);
        LOG.info("nop.git.clone:path={},state={}", path, git.getRepository().getRepositoryState());
    }

    @Override
    public void revert() {
        Git git = new Git(repository);
        RevertCommand command = git.revert();
        gitCall(command);
    }

    @Override
    public String getCurrentBranch() {
        try {
            return repository.getBranch();
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    protected RuntimeException handleError(Exception e) {
        return NopException.adapt(e);
    }

    @Override
    public void checkout(String branchName) {
        Git git = new Git(repository);
        CheckoutCommand command = git.checkout();
        Ref ref = gitCall(command);
        LOG.info("nop.git.checkout:path={},ref={}", path, ref);
    }

    @Override
    public void branchCreate(String branchName) {
        Git git = new Git(repository);
        CreateBranchCommand command = git.branchCreate();
        command.setName(branchName);
        Ref ref = gitCall(command);
        LOG.info("nop.git.branch-create:path={},ref={}", path, ref);
    }

    @Override
    public void merge(String message) {
        Git git = new Git(repository);
        MergeCommand command = git.merge();
        command.setMessage(message);

        MergeResult result = gitCall(command);
        LOG.info("nop.git.merge-result:path={},result={}", path, result);
    }

    @Override
    public void commit(String message) {
        Git git = new Git(repository);
        CommitCommand command = git.commit();
        command.setMessage(message);

        RevCommit result = gitCall(command);
        LOG.info("nop.git.commit-result:path={},result={}", path, result);
    }

    @Override
    public void push() {
        Git git = new Git(repository);
        PushCommand command = git.push();
        command.setCredentialsProvider(credentialsProvider);

        Iterable<PushResult> result = gitCall(command);
        LOG.info("nop.git.push-result:path={},result={}", path, result);
    }

    @Override
    public void pull() {
        Git git = new Git(repository);
        PullCommand command = git.pull();
        command.setCredentialsProvider(credentialsProvider);

        PullResult result = gitCall(command);
        LOG.info("nop.git.push-result:path={},result={}", path, result);
    }

    @Override
    public IResource getResource(String path) {
        ResourceHelper.checkValidRelativeName(path);
        return new FileResource(new File(rootDir, path));
    }
}
