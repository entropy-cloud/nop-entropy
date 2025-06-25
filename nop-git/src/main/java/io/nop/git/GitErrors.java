package io.nop.git;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GitErrors {
    // 错误码参数常量
    String ARG_PATH = "path";
    String ARG_REVISION = "revision";
    String ARG_OLD_COMMIT = "oldCommit";
    String ARG_NEW_COMMIT = "newCommit";

    // Git操作错误码
    ErrorCode ERR_GIT_FILE_NOT_FOUND = define(
            "nop.err.git.file-not-found",
            "文件不存在:{path}",
            ARG_PATH, ARG_REVISION);

    ErrorCode ERR_GIT_INVALID_COMMIT_ID = define(
            "nop.err.git.invalid-commit-id",
            "无效的提交ID: oldCommit={oldCommit}, newCommit={newCommit}",
            ARG_OLD_COMMIT, ARG_NEW_COMMIT);

    ErrorCode ERR_GIT_DIFF_FAILED = define(
            "nop.err.git.diff-failed",
            "获取差异失败: {path}",
            ARG_PATH);

    ErrorCode ERR_GIT_APPLY_FAILED = define(
            "nop.err.git.apply-failed",
            "应用差异失败: {path}",
            ARG_PATH);

    ErrorCode ERR_GIT_REPO_NOT_INITIALIZED = define(
            "nop.err.git.repo-not-initialized",
            "Git仓库未初始化");

    ErrorCode ERR_GIT_REMOTE_OPERATION_FAILED = define(
            "nop.err.git.remote-operation-failed",
            "远程操作失败: {operation}",
            "operation");

    ErrorCode ERR_GIT_NO_HEAD_COMMIT = define(
            "nop.err.git.no-head-commit",
            "Git仓库没有HEAD提交:{path}",
            ARG_PATH);
}
