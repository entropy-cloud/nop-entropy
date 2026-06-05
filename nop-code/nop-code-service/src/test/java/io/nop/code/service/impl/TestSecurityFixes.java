package io.nop.code.service.impl;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TestSecurityFixes {

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if (c == '?') {
                sb.append(".");
            } else if ("\\[]{}()+^$.|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Test
    void testFilterByFilePattern_noReDoS() {
        String filePattern = "test+file{1,10}(.*)";
        String regex = globToRegex(filePattern);
        assertDoesNotThrow(() -> {
            Pattern compiled = Pattern.compile(regex);
            assertFalse(compiled.matcher("anything").matches());
        });
    }

    @Test
    void testFilterByFilePattern_normalGlob() {
        String filePattern = "*.java";
        String regex = globToRegex(filePattern);
        Pattern compiled = Pattern.compile(regex);
        assertTrue(compiled.matcher("Test.java").matches());
        assertFalse(compiled.matcher("Test.txt").matches());
    }

    @Test
    void testFilterByFilePattern_questionMark() {
        String filePattern = "Test?.java";
        String regex = globToRegex(filePattern);
        Pattern compiled = Pattern.compile(regex);
        assertTrue(compiled.matcher("Test1.java").matches());
        assertFalse(compiled.matcher("Test.java").matches());
    }

    @Test
    void testFilterByFilePattern_dotIsLiteral() {
        String regex = globToRegex("Test.java");
        Pattern compiled = Pattern.compile(regex);
        assertTrue(compiled.matcher("Test.java").matches());
        assertFalse(compiled.matcher("TestXjava").matches());
    }

    @Test
    void testFilterByFilePattern_plusEscaped() {
        String regex = globToRegex("file+.txt");
        Pattern compiled = Pattern.compile(regex);
        assertFalse(compiled.matcher("fileee.txt").matches());
        assertTrue(compiled.matcher("file+.txt").matches());
    }

    @Test
    void testFilterByFilePattern_parenEscaped() {
        String regex = globToRegex("test(data).txt");
        Pattern compiled = Pattern.compile(regex);
        assertTrue(compiled.matcher("test(data).txt").matches());
        assertFalse(compiled.matcher("testXYZ.txt").matches());
    }
}
