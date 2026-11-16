package io.nop.ib.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect 27 semantics check: TagFilter.excludeTags must drop tagged tests at
 * discovery — this is the mechanism the fork runner uses to mirror surefire's
 * excludedGroups (zeebe excludes slow/performance tag groups).
 */
class TestExcludedTagFilter {

    static class Probe {
        @Test
        void plain() {
        }

        @Tag("slow")
        @Test
        void slowOne() {
        }
    }

    @Test
    void excludeTagsDropsTaggedTestsAtDiscovery() {
        Launcher launcher = LauncherFactory.create();
        LauncherDiscoveryRequestBuilder plainReq = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(Probe.class));
        TestPlan plainPlan = launcher.discover(plainReq.build());
        long plainTests = plainPlan.countTestIdentifiers(TestIdentifier::isTest);

        LauncherDiscoveryRequestBuilder filteredReq = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(Probe.class))
                .filters(TagFilter.excludeTags(List.of("slow")));
        TestPlan filteredPlan = launcher.discover(filteredReq.build());
        long filteredTests = filteredPlan.countTestIdentifiers(TestIdentifier::isTest);

        assertEquals(2, plainTests, "both tests discoverable without filter");
        assertTrue(filteredTests < plainTests,
                "tagged test must be excluded at discovery, got " + filteredTests);
    }
}
