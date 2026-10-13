package io.nop.ai.code_analyzer.maven;

public class MavenModule {
    private MavenDependencyNode moduleNode;
    private String modulePath;

    public MavenModule() {
    }

    public MavenModule(String modulePath, MavenDependencyNode node) {
        this.modulePath = modulePath;
        this.moduleNode = node;
    }

    public MavenDependencyNode getModuleNode() {
        return moduleNode;
    }

    public MavenModule cloneInstance() {
        return new MavenModule(modulePath, moduleNode.cloneInstance());
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
