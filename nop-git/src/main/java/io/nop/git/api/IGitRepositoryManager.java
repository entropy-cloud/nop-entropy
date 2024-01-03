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
