/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.git.api.IGitRepository;
import io.nop.git.api.IGitRepositoryManager;
import jakarta.annotation.PostConstruct;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;

public class GitRepositoryManagerImpl implements IGitRepositoryManager {
    static final Logger LOG = LoggerFactory.getLogger(GitRepositoryManagerImpl.class);

    private GitServerConfig config;

    public void setServerConfig(GitServerConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        Guard.notNull(config, "server config");
        Guard.notEmpty(config.getRemotePath(), "remotePath");
        Guard.notEmpty(config.getRootDir(), "rootDir");
    }

    @Override
    public IGitRepository getGitRepository(String path) {
        Guard.notEmpty(path, "repository path");
        ResourceHelper.checkValidRelativeName(path);
        String remoteUrl = getRemoteUrl(path);
        File localDir = getLocalDir(path);
        CredentialsProvider credentialsProvider = getCredentialProvider();
        return new GitRepositoryImpl(config, remoteUrl, localDir, path, credentialsProvider);
    }

    protected String getRemoteUrl(String path) {
        if (!path.endsWith(Constants.DOT_GIT)) {
            path += Constants.DOT_GIT;
        }
        return StringHelper.appendPath(config.getRemotePath(), path);
    }

    protected File getLocalDir(String path) {
        path = StringHelper.removeEnd(path, Constants.DOT_GIT);
        return new File(config.getRootDir(), path);
    }

    protected CredentialsProvider getCredentialProvider() {
        if (config.getUsername() != null)
            return new UsernamePasswordCredentialsProvider(config.getUsername(), config.getPassword());
        return null;
    }

    @Override
    public boolean existsRemoteRepository(String path) {
        String url = getRemoteUrl(path);

        LsRemoteCommand command = new LsRemoteCommand(null);
        command.setCredentialsProvider(getCredentialProvider());
        command.setRemote(url);
        if (config.getTimeout() > 0)
            command.setTimeout(config.getTimeout());

        try {
            Collection<Ref> list = command.call();
            LOG.trace("nop.git.ls-remote:url={},{}", url, list);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}