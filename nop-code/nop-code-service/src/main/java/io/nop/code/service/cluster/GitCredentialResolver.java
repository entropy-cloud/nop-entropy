package io.nop.code.service.cluster;

/**
 * N6.5: 私有仓库 git 凭据解析 SPI。凭据经环境变量通道传递给 git（ASKPASS 形态），
 * 绝不出现在命令行参数或日志中。
 */
public interface GitCredentialResolver {

    /**
     * @return 凭据或 null（本地仓/无凭据场景）。
     */
    GitCredential resolve(String repoPath);

    class GitCredential {
        private final String username;
        private final String password;

        public GitCredential(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
