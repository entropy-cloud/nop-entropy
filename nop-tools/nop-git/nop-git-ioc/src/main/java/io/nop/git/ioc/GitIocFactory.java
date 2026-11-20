package io.nop.git.ioc;

import io.nop.git.api.IGitRepositoryService;
import io.nop.git.core.JGitRepositoryService;

import java.io.File;
import java.nio.file.Path;

/**
 * Bean factory exposed through {@code git-ioc.beans.xml} (roadmap item 29):
 * the platform IoC container declares this factory and derives
 * {@link IGitRepositoryService} instances from it, so Nop services get the
 * policy-guarded facade via DI instead of shelling out to {@code git}.
 *
 * <p>Config properties are plain setters wired by the beans file; platform
 * config can override them via {@code @InjectValue}-style property
 * expressions in the deployment beans layer. nop-git-core itself stays free
 * of repository-internal dependencies (JDK 21 baseline, plan 2576) — this
 * integration module runs on the platform Java baseline by adjudication.
 */
public class GitIocFactory {

    private String defaultBranch = "master";

    private String policyPath = ".nop-git/policy.yaml";

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getPolicyPath() {
        return policyPath;
    }

    public void setPolicyPath(String policyPath) {
        this.policyPath = policyPath;
    }

    /**
     * Open the repository containing the given working directory with a
     * policy engine loaded from {@code <workDir>/<policyPath>}.
     */
    public IGitRepositoryService openRepository(File workDir) {
        Path root = workDir.toPath().toAbsolutePath().normalize();
        var engine = io.nop.git.policy.DefaultGitPolicyEngine.load(
                root, new java.io.PrintWriter(System.err, true));
        return IGitRepositoryService.open(workDir, engine);
    }

    /**
     * Initialize a new repository with the configured default branch.
     */
    public IGitRepositoryService initRepository(File workDir) {
        return IGitRepositoryService.init(workDir, defaultBranch);
    }
}
