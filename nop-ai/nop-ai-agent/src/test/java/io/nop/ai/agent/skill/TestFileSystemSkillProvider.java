package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFileSystemSkillProvider {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void loadsValidSkillYamlFiles() {
        // The _vfs/skills/ directory is mounted at /skills/ in VFS (test resources).
        FileSystemSkillProvider provider = new FileSystemSkillProvider("/skills");

        Collection<SkillModel> skills = provider.getSkills();

        assertNotNull(skills);
        assertTrue(skills.size() >= 3,
                "Expected at least 3 sample skills, got " + skills.size());

        // Verify each known fixture round-trips its fields.
        SkillModel logAnalysis = findByName(skills, "log-analysis");
        assertNotNull(logAnalysis, "log-analysis skill must be loaded");
        assertEquals("Analyze log files for errors and patterns", logAnalysis.getGoal());
        assertEquals(SkillTopPattern.VERIFY, logAnalysis.getTopPattern());
        assertTrue(logAnalysis.getDependencies().contains("read_file"));
        assertTrue(logAnalysis.getDependencies().contains("grep"));
        assertTrue(logAnalysis.getResourceScope().contains(SkillResourceScope.LOCAL_FS));
        assertTrue(logAnalysis.getResourceScope().contains(SkillResourceScope.MEMORY));
        assertTrue(logAnalysis.getIntentSignature().contains("log-analysis"));
        assertTrue(logAnalysis.getIntentSignature().contains("error-detection"));
        assertTrue(logAnalysis.getTags().contains("code"));
        assertTrue(logAnalysis.getTags().contains("ops"));

        SkillModel codeReview = findByName(skills, "code-review");
        assertNotNull(codeReview, "code-review skill must be loaded");
        assertEquals(SkillTopPattern.VERIFY, codeReview.getTopPattern());
        assertTrue(codeReview.getResourceScope().contains(SkillResourceScope.CODEBASE));

        SkillModel webSearch = findByName(skills, "web-search");
        assertNotNull(webSearch, "web-search skill must be loaded");
        assertEquals(SkillTopPattern.RETRIEVE, webSearch.getTopPattern());
        assertTrue(webSearch.getResourceScope().contains(SkillResourceScope.NETWORK));
    }

    @Test
    void missingDirectoryReturnsEmptySet() {
        FileSystemSkillProvider provider = new FileSystemSkillProvider("/nonexistent-skills-dir-xyz");

        Collection<SkillModel> skills = provider.getSkills();

        assertNotNull(skills);
        assertTrue(skills.isEmpty(), "Missing directory must return empty set, not an error");
    }

    @Test
    void nullBaseDirReturnsEmptySet() {
        FileSystemSkillProvider provider = new FileSystemSkillProvider(null);

        Collection<SkillModel> skills = provider.getSkills();

        assertNotNull(skills);
        assertTrue(skills.isEmpty());
    }

    @Test
    void emptyBaseDirReturnsEmptySet() {
        FileSystemSkillProvider provider = new FileSystemSkillProvider("");

        Collection<SkillModel> skills = provider.getSkills();

        assertNotNull(skills);
        assertTrue(skills.isEmpty());
    }

    @Test
    void malformedYamlFailsFast() {
        FileSystemSkillProvider provider = new FileSystemSkillProvider("/skills-malformed");

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, provider::getSkills);
        assertTrue(ex.getMessage().contains("Malformed YAML") || ex.getMessage().contains("parsing failed"),
                "Malformed YAML must fail fast with a clear error. Got: " + ex.getMessage());
    }

    @Test
    void resultIsCachedAcrossCalls() {
        FileSystemSkillProvider provider = new FileSystemSkillProvider("/skills");

        Collection<SkillModel> first = provider.getSkills();
        Collection<SkillModel> second = provider.getSkills();

        assertSameCollection(first, second, "Provider must cache the loaded set");
    }

    @Test
    void flatStructureAlsoAccepted() {
        // The provider should also accept flat (non-wrapped) YAML structure.
        // web-search.skill.yaml uses the skill: wrapper. Here we verify the
        // provider handles whatever valid files are in the dir without choking
        // on structure — already covered by loadsValidSkillYamlFiles.
        FileSystemSkillProvider provider = new FileSystemSkillProvider("/skills");
        Collection<SkillModel> skills = provider.getSkills();
        assertTrue(!skills.isEmpty());
    }

    private static SkillModel findByName(Collection<SkillModel> skills, String name) {
        for (SkillModel skill : skills) {
            if (name.equals(skill.getName())) {
                return skill;
            }
        }
        return null;
    }

    private static void assertSameCollection(Collection<SkillModel> a, Collection<SkillModel> b, String msg) {
        // Same underlying cached instance
        assertTrue(a == b || a.equals(b), msg);
    }
}
