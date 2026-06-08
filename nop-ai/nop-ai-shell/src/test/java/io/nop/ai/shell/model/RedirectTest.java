package io.nop.ai.shell.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedirectTest {

    @Test
    void testParseFdOutput() {
        Redirect r = Redirect.parse("2>&1");
        assertEquals(2, r.sourceFd());
        assertEquals(Redirect.Type.FD_OUTPUT, r.type());
        assertEquals("1", r.target());
    }

    @Test
    void testParseFdOutputReverse() {
        Redirect r = Redirect.parse("1>&2");
        assertEquals(1, r.sourceFd());
        assertEquals(Redirect.Type.FD_OUTPUT, r.type());
        assertEquals("2", r.target());
    }

    @Test
    void testParseMerge() {
        Redirect r = Redirect.parse("&>out.txt");
        assertNull(r.sourceFd());
        assertEquals(Redirect.Type.MERGE, r.type());
        assertEquals("out.txt", r.target());
    }

    @Test
    void testParseMergeAppend() {
        Redirect r = Redirect.parse("&>>log.txt");
        assertNull(r.sourceFd());
        assertEquals(Redirect.Type.MERGE_APPEND, r.type());
        assertEquals("log.txt", r.target());
    }

    @Test
    void testParseAppend() {
        Redirect r = Redirect.parse(">>log.txt");
        assertNull(r.sourceFd());
        assertEquals(Redirect.Type.APPEND, r.type());
        assertEquals("log.txt", r.target());
    }

    @Test
    void testParseOutput() {
        Redirect r = Redirect.parse(">out.txt");
        assertNull(r.sourceFd());
        assertEquals(Redirect.Type.OUTPUT, r.type());
        assertEquals("out.txt", r.target());
    }

    @Test
    void testParseInput() {
        Redirect r = Redirect.parse("<in.txt");
        assertNull(r.sourceFd());
        assertEquals(Redirect.Type.INPUT, r.type());
        assertEquals("in.txt", r.target());
    }

    @Test
    void testParseStderrOutput() {
        Redirect r = Redirect.parse("2>err.txt");
        assertEquals(2, r.sourceFd());
        assertEquals(Redirect.Type.OUTPUT, r.type());
        assertEquals("err.txt", r.target());
    }

    @Test
    void testParseStderrAppend() {
        Redirect r = Redirect.parse("2>>err.txt");
        assertEquals(2, r.sourceFd());
        assertEquals(Redirect.Type.APPEND, r.type());
        assertEquals("err.txt", r.target());
    }

    @Test
    void testParseNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> Redirect.parse(null));
    }

    @Test
    void testParseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Redirect.parse(""));
    }

    @Test
    void testParseInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> Redirect.parse("abc"));
    }
}
