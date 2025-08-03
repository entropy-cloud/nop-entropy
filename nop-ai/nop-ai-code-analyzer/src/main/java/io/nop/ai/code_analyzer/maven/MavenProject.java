package io.nop.ai.code_analyzer.maven;

import io.nop.api.core.util.Guard;
import io.nop.shell.ShellRunner;

import java.io.File;

import static io.nop.ai.code_analyzer.CodeAnalyzerConstants.DEPENDENCY_TREE_FILE_PATH;

public class MavenProject {
    private final File projectDir;

    private MavenModuleStructure moduleStructure;

    public MavenProject(File projectDir) {
        this.projectDir = Guard.notNull(projectDir, "projectDir");
    }

    public File getProjectDir() {
        return projectDir;
    }

    public void generateDependencyTree() {
        ShellRunner.runCommand("mvn -DoutputFile=" + DEPENDENCY_TREE_FILE_PATH + " dependency:tree", projectDir);
    }

    public MavenModuleStructure loadModuleStructure() {
        if (this.moduleStructure != null)
            return this.moduleStructure;

        MavenModuleStructure structure = new MavenModuleStructure();
        structure.load(projectDir);

        if (structure.getModuleCount() > 0)
            this.moduleStructure = structure;
        return structure;
    }
}
