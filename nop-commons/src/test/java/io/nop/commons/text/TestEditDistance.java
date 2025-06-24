package io.nop.commons.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEditDistance {

    @Test
    void testEmptyStrings() {
        assertEquals(0, EditDistance.calculate("", ""));
    }

    @Test
    void testFirstStringEmpty() {
        assertEquals(5, EditDistance.calculate("", "hello"));
    }

    @Test
    void testSecondStringEmpty() {
        assertEquals(5, EditDistance.calculate("world", ""));
    }

    @Test
    void testSameStrings() {
        assertEquals(0, EditDistance.calculate("kitten", "kitten"));
    }

    @Test
    void testDifferentStrings1() {
        assertEquals(3, EditDistance.calculate("kitten", "sitting"));
    }

    @Test
    void testDifferentStrings2() {
        assertEquals(3, EditDistance.calculate("saturday", "sunday"));
    }

    @Test
    void testDifferentStrings3() {
        assertEquals(1, EditDistance.calculate("book", "books"));
    }

    @Test
    void testDifferentStrings4() {
        assertEquals(1, EditDistance.calculate("book", "cook"));
    }

    @Test
    void testDifferentStrings5() {
        assertEquals(2, EditDistance.calculate("cat", "act"));
    }

    @Test
    void testUnicodeStrings() {
        assertEquals(1, EditDistance.calculate("café", "cafe"));
    }
}