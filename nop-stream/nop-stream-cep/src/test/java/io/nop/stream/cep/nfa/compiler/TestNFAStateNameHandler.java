package io.nop.stream.cep.nfa.compiler;

import io.nop.stream.cep.pattern.MalformedPatternException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNFAStateNameHandler {

    private NFAStateNameHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new NFAStateNameHandler();
    }

    @Test
    public void testGetUniqueInternalNameFirstTime() {
        String name = handler.getUniqueInternalName("stateA");
        assertEquals("stateA", name);
    }

    @Test
    public void testGetUniqueInternalNameDuplicatesGetSuffix() {
        String name1 = handler.getUniqueInternalName("stateA");
        String name2 = handler.getUniqueInternalName("stateA");

        assertEquals("stateA", name1);
        assertEquals("stateA:0", name2);
        assertNotEquals(name1, name2);
    }

    @Test
    public void testGetUniqueInternalNameMultipleDuplicates() {
        String name1 = handler.getUniqueInternalName("s");
        String name2 = handler.getUniqueInternalName("s");
        String name3 = handler.getUniqueInternalName("s");

        assertEquals("s", name1);
        assertEquals("s:0", name2);
        assertEquals("s:1", name3);
    }

    @Test
    public void testGetUniqueInternalNameDifferentBaseNames() {
        String a = handler.getUniqueInternalName("a");
        String b = handler.getUniqueInternalName("b");
        String c = handler.getUniqueInternalName("c");

        assertEquals("a", a);
        assertEquals("b", b);
        assertEquals("c", c);
    }

    @Tag("low-value")
    @Test
    public void testGetOriginalNameFromInternalNoSuffix() {
        assertEquals("stateA", NFAStateNameHandler.getOriginalNameFromInternal("stateA"));
    }

    @Tag("low-value")
    @Test
    public void testGetOriginalNameFromInternalWithSuffix() {
        assertEquals("stateA", NFAStateNameHandler.getOriginalNameFromInternal("stateA:0"));
        assertEquals("stateA", NFAStateNameHandler.getOriginalNameFromInternal("stateA:123"));
    }

    @Tag("low-value")
    @Test
    public void testGetOriginalNameFromInternalMultipleColons() {
        assertEquals("state", NFAStateNameHandler.getOriginalNameFromInternal("state:A:0"));
    }

    @Test
    public void testRoundTripNameGeneration() {
        String baseName = "myState";
        String internalName = handler.getUniqueInternalName(baseName);
        String originalName = NFAStateNameHandler.getOriginalNameFromInternal(internalName);
        assertEquals(baseName, originalName);
    }

    @Test
    public void testRoundTripMultipleGenerations() {
        String baseName = "repeated";
        for (int i = 0; i < 5; i++) {
            String internalName = handler.getUniqueInternalName(baseName);
            String originalName = NFAStateNameHandler.getOriginalNameFromInternal(internalName);
            assertEquals(baseName, originalName);
        }
    }

    @Test
    public void testCheckNameUniquenessAllowsUnique() {
        handler.checkNameUniqueness("unique1");
        handler.checkNameUniqueness("unique2");
    }

    @Test
    public void testCheckNameUniquenessRejectsDuplicate() {
        handler.checkNameUniqueness("dup");
        assertThrows(MalformedPatternException.class, () -> handler.checkNameUniqueness("dup"));
    }

    @Test
    public void testClearResetsNames() {
        handler.checkNameUniqueness("state");
        handler.clear();
        handler.checkNameUniqueness("state");
    }

    @Tag("low-value")
    @Test
    public void testStateNameDelim() {
        assertEquals(":", NFAStateNameHandler.STATE_NAME_DELIM);
        assertTrue("stateA:0".contains(NFAStateNameHandler.STATE_NAME_DELIM));
    }
}
