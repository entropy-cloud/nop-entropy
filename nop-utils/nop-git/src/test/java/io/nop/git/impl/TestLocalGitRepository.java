/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.impl;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.BeforeEach;

public class TestLocalGitRepository extends BaseTestCase {
    GitServerConfig gitConfig;
    GitRepositoryManagerImpl gitManager;

    @BeforeEach
    public void init() {
        gitManager = newManager();
    }

    GitRepositoryManagerImpl newManager() {
        GitServerConfig config = new GitServerConfig();
        config.setRemotePath("file://c:/can/git-test");
        config.setRootDir(getTargetFile("git-root/a"));
        gitConfig = config;

        GitRepositoryManagerImpl manager = new GitRepositoryManagerImpl();
        manager.setServerConfig(config);
        return manager;
    }

}
