package io.nop.ai.code_analyzer.project;

import io.nop.ai.code_analyzer.git.GitIgnoreFile;
import io.nop.api.core.util.Guard;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.Locale;

public class GitProject {
    private final File projectDir;

    public GitProject(File projectDir) {
        this.projectDir = Guard.notNull(projectDir, "projectDir");
    }

    public File getProjectDir() {
        return projectDir;
    }

    public GitIgnoreFile getGitIgnoreFile() {
        GitIgnoreFile file = GitIgnoreFile.create(new FileResource(projectDir));
        return file;
    }

    public File getReadMeFile() {
        File readme = new File(getProjectDir(), "README.md");
        if (readme.exists())
            return readme;
        readme = new File(getProjectDir(), "README");
        if (readme.exists())
            return readme;

        File[] files = getProjectDir().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toUpperCase(Locale.ROOT);
                if (name.equals("README") || name.startsWith("README."))
                    return file;
            }
        }
        return null;
    }

    public File getLicenseFile() {
        File license = new File(getProjectDir(), "LICENSE");
        if (license.exists())
            return license;
        license = new File(getProjectDir(), "LICENCE");
        if (license.exists())
            return license;
        return null;
    }
}
