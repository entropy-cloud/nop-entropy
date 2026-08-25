package io.nop.codegen.maven.parse;

import io.nop.codegen.maven.model.PomArtifactKey;
import io.nop.codegen.maven.model.PomDependencyModel;
import io.nop.codegen.maven.model.PomModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPomModelMerger {

    private static PomDependencyModel dep(String groupId, String artifactId, String version) {
        PomDependencyModel d = new PomDependencyModel();
        d.setArtifactKey(new PomArtifactKey(groupId, artifactId));
        d.setVersion(version);
        return d;
    }

    @Test
    public void testMergeKeepsChildOnlyDependency() {
        PomModel parent = new PomModel();
        parent.setArtifactKey(new PomArtifactKey("g", "parent"));
        parent.addDependency(dep("g", "common", "1.0"));

        PomModel child = new PomModel();
        child.setArtifactKey(new PomArtifactKey("g", "child"));
        child.addDependency(dep("g", "common", "1.0"));
        child.addDependency(dep("g", "child-only", "2.0"));

        PomModel merged = PomModelMerger.instance().merge(child, parent);

        // 修复前：child 独有依赖（oldDep == null 分支）不进入合并结果，依赖丢失
        assertNotNull(merged.getDependency(new PomArtifactKey("g", "child-only")));
        assertEquals("2.0", merged.getDependency(new PomArtifactKey("g", "child-only")).getVersion());
        // parent 提供的依赖仍在
        assertNotNull(merged.getDependency(new PomArtifactKey("g", "common")));
    }

    @Test
    public void testMergeDoesNotInheritParentModules() {
        PomModel parent = new PomModel();
        parent.setArtifactKey(new PomArtifactKey("g", "parent"));
        parent.setModules(List.of("module-a"));

        PomModel child = new PomModel();
        child.setArtifactKey(new PomArtifactKey("g", "child"));
        child.setModules(List.of("module-b"));

        PomModel merged = PomModelMerger.instance().merge(child, parent);

        // 修复前：parent 的 modules 被并入 child，PomModelResolver 会以 child 目录解析
        // parent 的 module 相对路径，指向不存在的 pom.xml
        assertEquals(List.of("module-b"), merged.getModules());
    }

    @Test
    public void testMergeChildWithoutModulesHasNoModules() {
        PomModel parent = new PomModel();
        parent.setArtifactKey(new PomArtifactKey("g", "parent"));
        parent.setModules(List.of("module-a"));

        PomModel child = new PomModel();
        child.setArtifactKey(new PomArtifactKey("g", "child"));

        PomModel merged = PomModelMerger.instance().merge(child, parent);

        assertNull(merged.getModules());
        assertTrue(merged.getDependencies().isEmpty());
    }
}
