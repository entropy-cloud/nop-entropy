package io.nop.stream.cep.nfa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDeweyNumber {

    @Test
    public void testParseFromString() {
        DeweyNumber d1 = DeweyNumber.fromString("1");
        assertEquals(1, d1.length());
        assertEquals("1", d1.toString());

        DeweyNumber d2 = DeweyNumber.fromString("1.2");
        assertEquals(2, d2.length());
        assertEquals("1.2", d2.toString());

        DeweyNumber d3 = DeweyNumber.fromString("1.2.3");
        assertEquals(3, d3.length());
        assertEquals("1.2.3", d3.toString());
    }

    @Test
    public void testIncrease() {
        DeweyNumber d = new DeweyNumber(1);
        DeweyNumber increased = d.increase();
        assertEquals(new DeweyNumber(2), increased);

        DeweyNumber d2 = new DeweyNumber(1);
        DeweyNumber increased2 = d2.increase(3);
        assertEquals(DeweyNumber.fromString("4"), increased2);
    }

    @Test
    public void testAddStage() {
        DeweyNumber d = new DeweyNumber(1);
        DeweyNumber staged = d.addStage();
        assertEquals(DeweyNumber.fromString("1.0"), staged);
        assertEquals(2, staged.length());
    }

    @Test
    public void testCompatiblePrefix() {
        DeweyNumber longer = DeweyNumber.fromString("1.2.3");
        DeweyNumber shorter = DeweyNumber.fromString("1.2");
        assertTrue(longer.isCompatibleWith(shorter));
    }

    @Test
    public void testCompatibleSameLengthGreaterLastDigit() {
        DeweyNumber d1 = DeweyNumber.fromString("1.3");
        DeweyNumber d2 = DeweyNumber.fromString("1.2");
        assertTrue(d1.isCompatibleWith(d2));
    }

    @Test
    public void testNotCompatibleShorter() {
        DeweyNumber shorter = DeweyNumber.fromString("1");
        DeweyNumber longer = DeweyNumber.fromString("1.2");
        assertFalse(shorter.isCompatibleWith(longer));
    }

    @Test
    public void testNotCompatibleDifferentPrefix() {
        DeweyNumber d1 = DeweyNumber.fromString("2.0");
        DeweyNumber d2 = DeweyNumber.fromString("1.0");
        assertFalse(d1.isCompatibleWith(d2));
    }

    @Test
    public void testGetRun() {
        DeweyNumber d = DeweyNumber.fromString("3.1.2");
        assertEquals(3, d.getRun());

        DeweyNumber d2 = DeweyNumber.fromString("7");
        assertEquals(7, d2.getRun());
    }

    @Test
    public void testLength() {
        assertEquals(1, new DeweyNumber(5).length());
        assertEquals(2, DeweyNumber.fromString("1.2").length());
        assertEquals(4, DeweyNumber.fromString("1.2.3.4").length());
    }

    @Test
    public void testEqualsAndHashCode() {
        DeweyNumber d1 = DeweyNumber.fromString("1.2.3");
        DeweyNumber d2 = DeweyNumber.fromString("1.2.3");
        DeweyNumber d3 = DeweyNumber.fromString("1.2.4");

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
        assertNotEquals(d1, d3);
    }
}
