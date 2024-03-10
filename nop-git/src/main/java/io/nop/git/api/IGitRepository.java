/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.api;

import io.nop.core.resource.IResource;

public interface IGitRepository extends AutoCloseable {

    boolean isInitialized();

    /**
     * 初始化仓库
     */
    void create();

    void cloneFromRemote();

    /**
     * 放弃当前所有修改
     */
    void revert();

    String getCurrentBranch();

    /**
     * 切换到某个指定分支
     *
     * @param branchName 指定分支的名称
     */
    void checkout(String branchName);

    /**
     * 创建一个新的分支
     *
     * @param branchName 新分支的名称
     */
    void branchCreate(String branchName);

    void merge(String message);

    void commit(String message);

    void reset(boolean hard);

    /**
     * 将本地的提交推送到远程仓库
     */
    void push();

    /**
     * 从远程仓库拉取到本地
     */
    void pull();

    /**
     * 得到Git仓库中的文件
     *
     * @param path 在仓库内部的香炉路径
     * @return 路径对应的文件对象
     */
    IResource getResource(String path);
}