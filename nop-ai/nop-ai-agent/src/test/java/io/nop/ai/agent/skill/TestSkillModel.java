package io.nop.ai.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSkillModel {

    @Test
    void fieldRoundTrip() {
        SkillModel skill = new SkillModel();
        skill.setName("code-review");
        skill.setGoal("Review code for quality and best practices");
        skill.setIntentSignature(List.of("review", "audit"));
        skill.setTopPattern(SkillTopPattern.VERIFY);
        skill.setDependencies(List.of("read_file", "git_diff"));
        skill.setTags(new LinkedHashSet<>(Arrays.asList("code", "review")));
        Set<SkillResourceScope> scopes = new LinkedHashSet<>(Arrays.asList(
                SkillResourceScope.CODEBASE, SkillResourceScope.MEMORY));
        skill.setResourceScope(scopes);

        assertEquals("code-review", skill.getName());
        assertEquals("Review code for quality and best practices", skill.getGoal());
        assertEquals(List.of("review", "audit"), skill.getIntentSignature());
        assertSame(SkillTopPattern.VERIFY, skill.getTopPattern());
        assertEquals(List.of("read_file", "git_diff"), skill.getDependencies());
        assertTrue(skill.getTags().contains("code"));
        assertTrue(skill.getTags().contains("review"));
        assertTrue(skill.getResourceScope().contains(SkillResourceScope.CODEBASE));
        assertTrue(skill.getResourceScope().contains(SkillResourceScope.MEMORY));
    }

    @Test
    void defaultsAreNullUntilSet() {
        SkillModel skill = new SkillModel();
        assertNull(skill.getName());
        assertNull(skill.getGoal());
        assertNull(skill.getIntentSignature());
        assertNull(skill.getTopPattern());
        assertNull(skill.getDependencies());
        assertNull(skill.getTags());
        assertNull(skill.getResourceScope());
    }

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
        assertEquals(SkillTopPattern.PREPARE, SkillTopPattern.valueOf("PREPARE"));
        assertEquals(SkillTopPattern.ACT, SkillTopPattern.valueOf("ACT"));
        assertEquals(SkillTopPattern.VERIFY, SkillTopPattern.valueOf("VERIFY"));
        assertEquals(SkillTopPattern.MANAGE, SkillTopPattern.valueOf("MANAGE"));
        assertEquals(SkillTopPattern.RETRIEVE, SkillTopPattern.valueOf("RETRIEVE"));
        assertEquals(SkillTopPattern.TRANSFORM, SkillTopPattern.valueOf("TRANSFORM"));
    }

    @Test
    void resourceScopeEnumHasPhase1Values() {
        // Design §4.1: MEMORY | LOCAL_FS | CODEBASE | NETWORK | CREDENTIALS
        assertEquals(5, SkillResourceScope.values().length);
        assertEquals(SkillResourceScope.MEMORY, SkillResourceScope.valueOf("MEMORY"));
        assertEquals(SkillResourceScope.LOCAL_FS, SkillResourceScope.valueOf("LOCAL_FS"));
        assertEquals(SkillResourceScope.CODEBASE, SkillResourceScope.valueOf("CODEBASE"));
        assertEquals(SkillResourceScope.NETWORK, SkillResourceScope.valueOf("NETWORK"));
        assertEquals(SkillResourceScope.CREDENTIALS, SkillResourceScope.valueOf("CREDENTIALS"));
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
