/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.impl;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.git.api.IGitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class TestGitRepositoryManager extends BaseTestCase {
    GitServerConfig gitConfig;
    GitRepositoryManagerImpl gitManager;

    @BeforeEach
    public void init() {
        gitManager = newManager();
    }

    GitRepositoryManagerImpl newManager() {
        GitServerConfig config = new GitServerConfig();
        config.setRemotePath("https://www.gitee.com/canonical-entropy/");
        config.setRootDir(getTargetFile("git-root/a"));
        gitConfig = config;

        GitRepositoryManagerImpl manager = new GitRepositoryManagerImpl();
        manager.setServerConfig(config);
        return manager;
    }

    @Test
    public void testExists() {
        boolean b = gitManager.existsRemoteRepository("nop-lowcode");
        assertTrue(b);

        b = gitManager.existsRemoteRepository("not-exists");
        assertTrue(!b);
    }

    @Test
    public void testPull() {
        FileHelper.deleteAll(gitConfig.getRootDir());

        IGitRepository repository = gitManager.getGitRepository("nop-lowcode");
        try {
            repository.cloneRepository("nop-lowcode");
            repository.pull();
            //  repository.push();
        } finally {
            IoHelper.safeCloseObject(repository);
        }
    }
}
