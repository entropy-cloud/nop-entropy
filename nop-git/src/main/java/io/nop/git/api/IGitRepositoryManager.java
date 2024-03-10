/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.api;

public interface IGitRepositoryManager {
    IGitRepository getGitRepository(String path);

    /**
     * 判断是否存在远程仓库
     *
     * @param path 相对路径
     */
    boolean existsRemoteRepository(String path);
}
