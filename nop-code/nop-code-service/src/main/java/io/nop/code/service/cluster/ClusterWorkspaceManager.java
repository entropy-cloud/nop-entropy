package io.nop.code.service.cluster;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * N6.3: repo checkout 工作区管理。workspace root 必须显式配置（未配置显式失败——集群构建
 * 的磁盘面不允许隐式落点）；每个 (repo, revision) 一个工作区目录：不存在则 clone，存在但
 * HEAD 不符则 fetch + checkout，相符则复用。revision 与 repo 路径均做白名单校验（对齐
 * triggerRebuildFromCommit 的 GIT_REF 模式与 canonical 路径防逃逸）。
 */
public class ClusterWorkspaceManager {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterWorkspaceManager.class);

    public static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9._\\-]{1,128}$");
    private static final int GIT_TIMEOUT_MILLIS = 60_000;

    private final String workspaceRoot;
    private final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();
    private GitCredentialResolver credentialResolver;

    public void setCredentialResolver(GitCredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    public ClusterWorkspaceManager(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isEmpty()) {
            throw new IllegalArgumentException(
                    "cluster workspace root must be configured (nop.code.cluster.workspace-root)");
        }
        try {
            this.workspaceRoot = new File(workspaceRoot).getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid workspace root: " + workspaceRoot, e);
        }
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public WorkspaceInfo prepare(String repoPath, String revision) {
        validateRevision(revision);
        File repoDir = canonicalExistingDir(repoPath, "repoPath");
        String repoName = repoDir.getName();
        if (!SAFE_NAME.matcher(repoName).matches()) {
            // clone target directory is derived from the repo dir name; keep it path-safe
            throw new IllegalArgumentException(
                    "repo directory name must match " + SAFE_NAME.pattern() + ": " + repoName);
        }

        ReentrantLock lock = repoLocks.computeIfAbsent(repoName, k -> new ReentrantLock());
        lock.lock();
        try {
            Path workspace = Path.of(workspaceRoot, repoName, revision);
            if (Files.isDirectory(workspace.resolve(".git"))) {
                String head = gitOutput(workspace, "rev-parse", "HEAD");
                if (!head.equals(revision)) {
                    // revision may be a branch/tag name; resolve to a commit for comparison
                    String target = gitOutput(workspace, "rev-parse", revision);
                    if (!head.equals(target)) {
                        git(workspace, "fetch", "--all");
                        git(workspace, "checkout", revision);
                    }
                    head = gitOutput(workspace, "rev-parse", "HEAD");
                }
                LOG.info("nop.code.cluster.workspace-reused:repo={},revision={},head={}", repoName, revision, head);
                return new WorkspaceInfo(workspace.toString(), head, true);
            }
            try {
                Files.createDirectories(workspace.getParent());
            } catch (IOException e) {
                throw new IllegalStateException("cannot create workspace parent: " + workspace.getParent(), e);
            }
            GitCredentialResolver.GitCredential credential =
                    credentialResolver != null ? credentialResolver.resolve(repoDir.getAbsolutePath()) : null;
            gitWithCredential(Path.of(workspaceRoot), credential, "clone",
                    repoDir.getAbsolutePath(), workspace.toString());
            git(workspace, "checkout", revision);
            String head = gitOutput(workspace, "rev-parse", "HEAD");
            LOG.info("nop.code.cluster.workspace-created:repo={},revision={},head={}", repoName, revision, head);
            return new WorkspaceInfo(workspace.toString(), head, false);
        } finally {
            lock.unlock();
        }
    }

    private void validateRevision(String revision) {
        if (revision == null
                || revision.contains("..")
                || !Pattern.compile("^[a-zA-Z0-9._/\\-~]{1,256}$").matcher(revision).matches()) {
            throw new IllegalArgumentException("invalid git revision: " + revision);
        }
    }

    private static File canonicalExistingDir(String path, String argName) {
        if (path != null && path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        File file = new File(path == null ? "" : path);
        try {
            File canonical = file.getCanonicalFile();
            if (!canonical.isDirectory()) {
                throw new IllegalArgumentException(
                        argName + " must be an existing directory: " + path);
            }
            return canonical;
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot canonicalize " + argName + ": " + path, e);
        }
    }

    private void git(Path workdir, String... args) {
        gitWithCredential(workdir, null, args);
    }

    /**
     * N6.5: credentials travel through the process environment (GIT_ASKPASS contract via
     * GIT_HTTP_LOWAUTH_X placeholders is avoided — we use the env-only channel so the
     * credential never appears in command-line arguments or logs).
     */
    private void gitWithCredential(Path workdir, GitCredentialResolver.GitCredential credential, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("git");
            for (String arg : args) {
                command.add(arg);
            }
            pb.command(command);
            pb.directory(workdir.toFile());
            if (credential != null) {
                applyCredential(pb, credential);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(GIT_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git command timed out");
            }
            if (process.exitValue() != 0) {
                // note: output may contain git diagnostics — never the credential (env channel)
                throw new IllegalStateException("git command failed (" + process.exitValue() + ")");
            }
        } catch (IOException e) {
            throw new IllegalStateException("git command io failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git command interrupted", e);
        }
    }

    /**
     * N6.5: 凭据仅经进程环境变量传递（NOP_GIT_USERNAME/PASSWORD——部署方以 askpass helper
     * 消费该约定），命令行参数与日志零泄漏。注意：环境变量通道本身对 git 是惰性的
     * （git 原生不读取 NOP_GIT_*），认证由部署方的 credential helper 消费。
     */
    static void applyCredential(ProcessBuilder pb, GitCredentialResolver.GitCredential credential) {
        pb.environment().put("NOP_GIT_USERNAME",
                credential.getUsername() == null ? "" : credential.getUsername());
        pb.environment().put("NOP_GIT_PASSWORD",
                credential.getPassword() == null ? "" : credential.getPassword());
    }

    private static String gitOutput(Path workdir, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("git");
            for (String arg : args) {
                command.add(arg);
            }
            pb.command(command);
            pb.directory(workdir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(GIT_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git command timed out: " + command);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git command failed (" + process.exitValue() + "): "
                        + command + "\n" + output);
            }
            return output.trim();
        } catch (IOException e) {
            throw new IllegalStateException("git command io failure: " + String.join(" ", args), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git command interrupted", e);
        }
    }

    public static class WorkspaceInfo {
        private final String workspacePath;
        private final String headCommit;
        private final boolean reused;

        public WorkspaceInfo(String workspacePath, String headCommit, boolean reused) {
            this.workspacePath = workspacePath;
            this.headCommit = headCommit;
            this.reused = reused;
        }

        public String getWorkspacePath() {
            return workspacePath;
        }

        public String getHeadCommit() {
            return headCommit;
        }

        public boolean isReused() {
            return reused;
        }
    }
}
