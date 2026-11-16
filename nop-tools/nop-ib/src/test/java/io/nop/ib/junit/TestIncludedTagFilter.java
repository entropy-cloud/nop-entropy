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
 * Wave6 6A-2 mechanism pin: TagFilter.includeTags must keep ONLY tagged tests
 * at discovery — the mechanism the fork runner uses to mirror surefire's
 * &lt;groups&gt; (an unparsed groups dispatched the full set where mvn ran only
 * the tag-matched subset — the positive twin of defect 27).
 */
class TestIncludedTagFilter {

    static class Probe {
        @Test
        void plain() {
        }

        @Tag("fast")
        @Test
        void fastOne() {
        }
    }

    @Test
    void includeTagsKeepsOnlyTaggedTestsAtDiscovery() {
        Launcher launcher = LauncherFactory.create();
        LauncherDiscoveryRequestBuilder plainReq = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(Probe.class));
        TestPlan plainPlan = launcher.discover(plainReq.build());
        long plainTests = plainPlan.countTestIdentifiers(TestIdentifier::isTest);

        LauncherDiscoveryRequestBuilder filteredReq = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(Probe.class))
                .filters(TagFilter.includeTags(List.of("fast")));
        TestPlan filteredPlan = launcher.discover(filteredReq.build());
        long filteredTests = filteredPlan.countTestIdentifiers(TestIdentifier::isTest);

        assertEquals(2, plainTests, "both tests discoverable without filter");
        assertTrue(filteredTests < plainTests,
                "untagged test must be excluded by the include filter, got " + filteredTests);
    }
}
