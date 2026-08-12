package io.nop.core.resource.component.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestResourceVersionHelper {

    @Test
    public void testParseVersionedName_withoutVersion() {
        VersionedName name = ResourceVersionHelper.parseVersionedName("resolve-wf:myFlow", "resolve-wf:");
        assertEquals("myFlow", name.getName());
        assertEquals(-1L, name.getVersion());
    }

    @Test
    public void testParseVersionedName_withVersion() {
        VersionedName name = ResourceVersionHelper.parseVersionedName("resolve-wf:myFlow/v1", "resolve-wf:");
        assertEquals("myFlow", name.getName());
        assertEquals(1L, name.getVersion());
    }

    @Test
    public void testParseVersionedName_namespacedNameWithoutVersion() {
        VersionedName name = ResourceVersionHelper.parseVersionedName("resolve-wf:test/designer-flow", "resolve-wf:");
        assertEquals("test/designer-flow", name.getName());
        assertEquals(-1L, name.getVersion());
    }

    @Test
    public void testParseVersionedName_namespacedNameWithVersion() {
        VersionedName name = ResourceVersionHelper.parseVersionedName("resolve-wf:test/designer-flow/v2", "resolve-wf:");
        assertEquals("test/designer-flow", name.getName());
        assertEquals(2L, name.getVersion());
    }
}
