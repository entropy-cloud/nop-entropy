package io.nop.ai.code_analyzer.maven;

import io.nop.ai.code_analyzer.project.GitProject;
import io.nop.shell.ShellRunner;

import java.io.File;
import java.util.List;

import static io.nop.ai.code_analyzer.CodeAnalyzerConstants.DEPENDENCY_TREE_FILE_PATH;

public class MavenProject extends GitProject {

    private MavenModuleStructure moduleStructure;

    private JavaSourceFileFinder javaFileFinder;

    public MavenProject(File projectDir) {
        super(projectDir);
    }

    public void generateDependencyTree() {
        ShellRunner.runCommand("mvn -DoutputFile=" + DEPENDENCY_TREE_FILE_PATH + " dependency:tree", getProjectDir());
    }

    public File getPomFile() {
        File file = new File(getProjectDir(), "pom.xml");
        if (file.exists())
            return file;
        return null;
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
        structure.load(getProjectDir());

        if (structure.getModuleCount() > 0)
            this.moduleStructure = structure;
        return structure;
    }
}
