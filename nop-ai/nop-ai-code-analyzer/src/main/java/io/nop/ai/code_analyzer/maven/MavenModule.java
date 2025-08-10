package io.nop.ai.code_analyzer.maven;

public class MavenModule {
    private MavenDependencyNode moduleNode;
    private String modulePath;

    public MavenDependencyNode getModuleNode() {
        return moduleNode;
    }

    public void setModuleNode(MavenDependencyNode moduleNode) {
        this.moduleNode = moduleNode;
    }

    public String getModuleId() {
        return moduleNode.getModuleId();
    }

    public String getArtifactId() {
        return moduleNode.getArtifactId();
    }

    public String getModulePath() {
        return modulePath;
    }

    public void setModulePath(String modulePath) {
        this.modulePath = modulePath;
    }
}
