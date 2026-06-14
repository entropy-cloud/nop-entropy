package io.nop.ai.agent.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpSkillCurator {

    @Test
    void factoryReturnsSingleton() {
        ISkillCurator a = NoOpSkillCurator.noOp();
        ISkillCurator b = NoOpSkillCurator.noOp();
        assertSame(a, b);
    }

    @Test
    void implementsISkillCurator() {
        assertTrue(ISkillCurator.class.isAssignableFrom(NoOpSkillCurator.class));
        assertTrue(NoOpSkillCurator.noOp() instanceof ISkillCurator);
    }

    @Test
    void returnsEmptyNonNullResult() {
        SkillCurationResult result = NoOpSkillCurator.noOp().curate(null);
        assertNotNull(result);
        assertTrue(result.getAssessments().isEmpty());
    }

    @Test
    void returnsSuccessMarker() {
        SkillCurationResult result = NoOpSkillCurator.noOp().curate(null);
        assertTrue(result.isSuccess(), "NoOp curator must return success marker");
        assertTrue(result.getMetadata().isSuccess());
        assertEquals("no-op", result.getMetadata().getCuratorType());
    }

    @Test
    void consistentAcrossCalls() {
        ISkillCurator curator = NoOpSkillCurator.noOp();
        SkillCurationResult first = curator.curate(null);
        SkillCurationResult second = curator.curate(java.util.Collections.emptyList());
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertTrue(first.getAssessments().isEmpty());
        assertTrue(second.getAssessments().isEmpty());
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
