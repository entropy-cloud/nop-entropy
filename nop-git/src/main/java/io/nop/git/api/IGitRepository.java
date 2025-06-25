/**
 * Git仓库操作接口，提供完整的版本控制功能
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.api;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.resource.IResource;

import java.util.List;

/**
 * 简化版Git仓库操作接口，仅支持单分支模型文件管理
 */
public interface IGitRepository extends AutoCloseable {

    /* 仓库基本信息操作 */

    /**
     * 检查仓库是否已初始化
     */
    boolean isInitialized();

    /**
     * 初始化一个新的Git仓库
     */
    void init();

    /**
     * 获取仓库根目录路径
     */
    String getRepositoryPath();

    /* 远程仓库操作 */

    /**
     * 从远程仓库克隆(默认主分支)
     */
    void cloneRepository(String remoteUrl);

    /**
     * 推送更改到远程仓库
     */
    void push();

    /**
     * 从远程拉取更新
     */
    void pull();

    /* 版本控制操作 */

    /**
     * 获取当前最新提交的hash
     */
    String getLatestCommitHash();

    /**
     * 检查是否有未提交的更改
     */
    boolean hasUncommittedChanges();

    /**
     * 添加文件到暂存区
     */
    void add(String... paths);

    /**
     * 提交更改
     */
    CommitResult commit(String message, String author);

    /* 文件内容操作 */

    /**
     * 获取仓库中的文件资源
     */
    IResource getResource(String path);

    /**
     * 获取文件在指定版本的内容
     */
    String getFileContent(String path, String revision);

    /**
     * 获取两个提交之间变更的文件列表
     */
    List<ChangedFile> getChangedFilesBetweenCommits(String oldCommit, String newCommit);

    /* Diff 相关操作 */

    /**
     * 获取工作区与最新提交的差异
     */
    List<GitDiff> getWorkingTreeDiff();

    /**
     * 获取两个提交之间的差异
     */
    List<GitDiff> getCommitDiff(String oldCommit, String newCommit);

    /**
     * 应用差异到工作区
     */
    boolean applyDiff(GitDiff diff);

    /**
     * 批量应用差异
     */
    int applyDiffs(List<GitDiff> diffs);

    /**
     * 关闭仓库并释放资源
     */
    @Override
    void close();

    /* ========== 数据实体 ========== */

    @DataBean
    class ChangedFile {
        private String path;
        private String changeType;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getChangeType() {
            return changeType;
        }

        public void setChangeType(String changeType) {
            this.changeType = changeType;
        }
    }

    @DataBean
    class CommitResult {
        private String commitId;
        private String shortMessage;
        private String author;
        private long commitTime;

        public String getCommitId() {
            return commitId;
        }

        public void setCommitId(String commitId) {
            this.commitId = commitId;
        }

        public String getShortMessage() {
            return shortMessage;
        }

        public void setShortMessage(String shortMessage) {
            this.shortMessage = shortMessage;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public long getCommitTime() {
            return commitTime;
        }

        public void setCommitTime(long commitTime) {
            this.commitTime = commitTime;
        }
    }

    @DataBean
    class GitDiff {
        /**
         * 差异类型：ADD, MODIFY, DELETE, RENAME
         */
        private String changeType;

        /**
         * 新文件路径
         */
        private String newPath;

        /**
         * 旧文件路径
         */
        private String oldPath;

        /**
         * 统一diff格式内容
         */
        private String diffContent;

        public String getChangeType() {
            return changeType;
        }

        public void setChangeType(String changeType) {
            this.changeType = changeType;
        }

        public String getNewPath() {
            return newPath;
        }

        public void setNewPath(String newPath) {
            this.newPath = newPath;
        }

        public String getOldPath() {
            return oldPath;
        }

        public void setOldPath(String oldPath) {
            this.oldPath = oldPath;
        }

        public String getDiffContent() {
            return diffContent;
        }

        public void setDiffContent(String diffContent) {
            this.diffContent = diffContent;
        }
    }
}