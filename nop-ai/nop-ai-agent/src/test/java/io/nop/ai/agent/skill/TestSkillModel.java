package io.nop.ai.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSkillModel {

    @Test
    void collectToolDependenciesAddsNonNullNonEmpty() {
        SkillModel skill = new SkillModel();
        skill.setDependencies(Arrays.asList("read_file", null, "", "git_diff", "write_file"));

        Set<String> sink = new LinkedHashSet<>();
        skill.collectToolDependencies(sink);

        assertEquals(3, sink.size());
        assertTrue(sink.contains("read_file"));
        assertTrue(sink.contains("git_diff"));
        assertTrue(sink.contains("write_file"));
    }

    @Test
    void collectToolDependenciesHandlesNullDependencies() {
        SkillModel skill = new SkillModel();
        Set<String> sink = new LinkedHashSet<>();
        // null dependencies must not throw and must not add anything.
        skill.collectToolDependencies(sink);
        assertTrue(sink.isEmpty());
    }

    @Test
    void collectResourceScopeAddsAll() {
        SkillModel skill = new SkillModel();
        Set<SkillResourceScope> scopes = new LinkedHashSet<>(Arrays.asList(
                SkillResourceScope.NETWORK, SkillResourceScope.CREDENTIALS));
        skill.setResourceScope(scopes);

        Set<SkillResourceScope> sink = new LinkedHashSet<>();
        skill.collectResourceScope(sink);

        assertEquals(2, sink.size());
        assertTrue(sink.contains(SkillResourceScope.NETWORK));
        assertTrue(sink.contains(SkillResourceScope.CREDENTIALS));
    }

    @Test
    void collectResourceScopeHandlesNull() {
        SkillModel skill = new SkillModel();
        Set<SkillResourceScope> sink = new LinkedHashSet<>();
        skill.collectResourceScope(sink);
        assertTrue(sink.isEmpty());
    }

    @Test
    void topPatternEnumHasPhase1Values() {
        // Design §4.1: PREPARE | ACT | VERIFY | MANAGE | RETRIEVE | TRANSFORM
        assertEquals(6, SkillTopPattern.values().length);
    }

    @Test
    void resourceScopeEnumHasPhase1Values() {
        // Design §4.1: MEMORY | LOCAL_FS | CODEBASE | NETWORK | CREDENTIALS
        assertEquals(5, SkillResourceScope.values().length);
    }

    @Test
    void copyTagsReturnsEmptyForNull() {
        Set<String> copy = SkillModel.copyTags(null);
        assertTrue(copy.isEmpty());
    }

    @Test
    void copyTagsReturnsMutableCopy() {
        Set<String> source = new LinkedHashSet<>(Arrays.asList("a", "b"));
        Set<String> copy = SkillModel.copyTags(source);
        assertEquals(2, copy.size());
        copy.add("c");
        assertEquals(2, source.size(), "copyTags must return an independent copy");
    }
}
