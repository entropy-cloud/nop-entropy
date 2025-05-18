package io.nop.javaparser.format;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.Test;

import static com.github.javaparser.utils.Utils.assertNotNull;
import static io.nop.javaparser.JavaParserErrors.ERR_JAVA_PARSER_PARSE_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaParserCodeFormatterTest {

    @Test
    public void testSuccessfulParsing() {
        // Setup
        JavaParserCodeFormatter formatter = new JavaParserCodeFormatter();
        SourceLocation loc = SourceLocation.fromPath("test.java");
        String sourceCode = "class Test{}";
        boolean ignoreErrors = false;

        // Execute
        String result = formatter.format(loc, sourceCode, ignoreErrors);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("class Test"));
    }

    @Test
    public void testFailedParsingWithoutIgnoreErrors() {
        // Setup
        JavaParserCodeFormatter formatter = new JavaParserCodeFormatter();
        SourceLocation loc = SourceLocation.fromPath("test.java");
        String sourceCode = "invalid code";
        boolean ignoreErrors = false;

        // Test & Verify
        NopException exception = assertThrows(NopException.class,
                () -> formatter.format(loc, sourceCode, ignoreErrors));

        assertNotNull(exception.getErrorLocation());
        assertEquals(ERR_JAVA_PARSER_PARSE_FAILED.getErrorCode(), exception.getErrorCode());
    }

    @Test
    public void testFormatWithMultipleClasses() {
        // Setup
        JavaParserCodeFormatter formatter = new JavaParserCodeFormatter();
        SourceLocation loc = SourceLocation.fromPath("test.java");
        String sourceCode = "class A{}\nclass B{}";
        boolean ignoreErrors = false;

        // Execute
        String result = formatter.format(loc, sourceCode, ignoreErrors);

        // Verify
        // The exact formatted output might depend on the DefaultPrettyPrinter implementation,
        // but we can verify it contains both class definitions
        assertEquals("class A {\n}\n\nclass B {\n}", result.trim());
    }
}