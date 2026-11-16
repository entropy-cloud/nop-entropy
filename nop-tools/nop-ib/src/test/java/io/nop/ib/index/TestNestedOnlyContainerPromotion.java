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
}
