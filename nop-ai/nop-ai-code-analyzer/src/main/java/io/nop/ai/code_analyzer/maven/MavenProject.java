package io.nop.ai.code_analyzer.maven;

import io.nop.ai.code_analyzer.git.GitIgnoreFile;
import io.nop.api.core.util.Guard;
import io.nop.core.resource.impl.FileResource;
import io.nop.shell.ShellRunner;

import java.io.File;
import java.util.List;

import static io.nop.ai.code_analyzer.CodeAnalyzerConstants.DEPENDENCY_TREE_FILE_PATH;

public class MavenProject {
    private final File projectDir;

    private MavenModuleStructure moduleStructure;

    private JavaSourceFileFinder javaFileFinder;

    public MavenProject(File projectDir) {
        this.projectDir = Guard.notNull(projectDir, "projectDir");
    }

    public File getProjectDir() {
        return projectDir;
    }

    public void generateDependencyTree() {
        ShellRunner.runCommand("mvn -DoutputFile=" + DEPENDENCY_TREE_FILE_PATH + " dependency:tree", projectDir);
    }

    public GitIgnoreFile getGitIgnoreFile() {
        GitIgnoreFile file = GitIgnoreFile.create(new FileResource(projectDir));
        return file;
    }

    public JavaSourceFileFinder getJavaFileFinder() {
        if (javaFileFinder == null) {
            javaFileFinder = new JavaSourceFileFinder(loadModuleStructure());
        }
        return javaFileFinder;
    }

    public File findJavaFileByClassName(String className) {
        return getJavaFileFinder().findJavaSourceFile(className);
    }

    public List<File> findJavaFileBySimpleName(String simpleName) {
        return getJavaFileFinder().findJavaSourceFilesBySimpleName(simpleName);
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
