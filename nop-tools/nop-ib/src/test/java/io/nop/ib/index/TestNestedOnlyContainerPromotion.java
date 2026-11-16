package io.nop.ib.index;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect 28 regression pin: a top-level test class whose tests live ENTIRELY in
 * {@code @Nested} members carries no test markers itself and must still be
 * dispatched — the JUnit platform discovers the nested tree from the outer
 * selector, same as Maven. zeebe-util lost 27 tests this way
 * (CollectionUtilTest 9 / FuturesUtilTest 6 / VersionCompatibilityCheckTest 12).
 */
class TestNestedOnlyContainerPromotion {

    private ClassInfo cls(String name, boolean testMethods, boolean nested) {
        ClassInfo info = new ClassInfo();
        info.setClassName(name);
        info.setTestMethods(testMethods);
        info.setNested(nested);
        // testClasses() only considers src/test sources
        info.setSourceFile("src/test/java/" + name.replace('.', '/') + ".java");
        return info;
    }

    @Test
    void nestedOnlyContainerIsDispatchedAndMembersNotDuplicated() {
        UnitIndex index = UnitIndex.empty("m");
        index.put(cls("a.OuterTest", false, false));            // no direct markers
        index.put(cls("a.OuterTest$Inner", true, true));        // nested, has tests
        index.put(cls("a.PlainHelper", false, false));          // no markers at all

        var names = index.testClasses().stream()
                .map(ClassInfo::getClassName)
                .toList();

        assertTrue(names.contains("a.OuterTest"),
                "@Nested-only container must be promoted, got " + names);
        assertFalse(names.contains("a.OuterTest$Inner"),
                "nested member must not double-dispatch (outer discovers it), got " + names);
        assertFalse(names.contains("a.PlainHelper"),
                "helper classes without any nested tests stay undispatched, got " + names);
        assertEquals(1, names.size());
    }

    /**
     * Defect 29 regression pin (wave6 6C-1): JUnit4 static inner classes carry
     * NO @Nested annotation — the old @Nested-only guard dispatched BOTH the
     * Enclosed container AND its members (zeebe-gateway-grpc ActivateJobsTest:
     * container 18 + members 10+8 = 36 vs mvn's 18). Binary-name $ nesting must
     * cover the guard for JUnit4 too.
     */
    @Test
    void junit4EnclosedMembersAreNotDoubleDispatched() {
        UnitIndex index = UnitIndex.empty("m");
        index.put(cls("z.ActivateJobsTest", false, false));              // Enclosed container
        index.put(cls("z.ActivateJobsTest$MultiTenancyDisabledTest", true, false)); // JUnit4 inner
        index.put(cls("z.ActivateJobsTest$MultiTenancyEnabledTest", true, false));  // JUnit4 inner

        var names = index.testClasses().stream()
                .map(ClassInfo::getClassName)
                .toList();

        assertTrue(names.contains("z.ActivateJobsTest"),
                "Enclosed container must be dispatched (vintage discovers members), got " + names);
        assertFalse(names.contains("z.ActivateJobsTest$MultiTenancyDisabledTest"),
                "JUnit4 inner must not double-dispatch (defect 29), got " + names);
        assertFalse(names.contains("z.ActivateJobsTest$MultiTenancyEnabledTest"),
                "JUnit4 inner must not double-dispatch (defect 29), got " + names);
        assertEquals(1, names.size());
    }
}
