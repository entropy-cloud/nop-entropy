package io.nop.ai.coder.code;

import io.nop.ai.coder.convert.AiOrmDocumentConverter;
import io.nop.ai.coder.convert.AiXdefDocumentConverter;
import io.nop.ai.coder.orm.AiOrmSqlType;
import io.nop.ai.coder.utils.AiCoderHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.impl.ResourceDocumentObject;
import io.nop.core.resource.impl.ByteArrayResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.nop.ai.coder.AiCoderErrors.ARG_DATA;
import static io.nop.ai.coder.AiCoderErrors.ARG_FROM;
import static io.nop.ai.coder.AiCoderErrors.ARG_HEADERS;
import static io.nop.ai.coder.AiCoderErrors.ARG_SIGNATURE;
import static io.nop.ai.coder.AiCoderErrors.ARG_SQL_TYPE;
import static io.nop.ai.coder.AiCoderErrors.ARG_TO;
import static io.nop.ai.coder.AiCoderErrors.ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH;
import static io.nop.ai.coder.AiCoderErrors.ERR_AI_CODER_METHOD_SIGNATURE_NOT_FOUND;
import static io.nop.ai.coder.AiCoderErrors.ERR_AI_CODER_UNBALANCED_BRACES;
import static io.nop.ai.coder.AiCoderErrors.ERR_AI_CODER_UNKNOWN_SQL_TYPE;
import static io.nop.ai.coder.AiCoderErrors.ERR_AI_CODER_UNSUPPORTED_CONVERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused value-level tests for the error codes introduced by plan
 * 2026-08-01-0936-2: {@link JavaMethodReplacer} signature/brace failures
 * and the unsupported-conversion rejection of the coder document converters.
 */
public class TestCoderErrorCodeConversion {

    @TempDir
    Path tempDir;

    private Path writeFile(String content) throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    public void testMissingMethodSignatureRejectedWithErrorCode() throws IOException {
        Path file = writeFile("public class Foo {}\n");

        NopException ex = assertThrows(NopException.class,
                () -> JavaMethodReplacer.applyMethodDiff(file.toString(), "public void missing()", "{}"));
        assertEquals(ERR_AI_CODER_METHOD_SIGNATURE_NOT_FOUND.getErrorCode(), ex.getErrorCode(),
                "missing signature must carry ERR_AI_CODER_METHOD_SIGNATURE_NOT_FOUND");
        assertEquals("public void missing()", ex.getParam(ARG_SIGNATURE),
                "the searched signature must be attached as a param");
        assertTrue(ex.getMessage().contains("Method signature not found: public void missing()"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testUnbalancedBracesRejectedWithErrorCode() throws IOException {
        Path file = writeFile("public void foo() {\n");

        NopException ex = assertThrows(NopException.class,
                () -> JavaMethodReplacer.applyMethodDiff(file.toString(), "public void foo()", "{}"));
        assertEquals(ERR_AI_CODER_UNBALANCED_BRACES.getErrorCode(), ex.getErrorCode(),
                "unbalanced braces must carry ERR_AI_CODER_UNBALANCED_BRACES");
        assertTrue(ex.getMessage().contains("Unbalanced braces in method body"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testAiOrmUnsupportedConversionRejectedWithErrorCode() {
        IDocumentObject doc = new ResourceDocumentObject("txt",
                new ByteArrayResource("/test.txt", "hello".getBytes(StandardCharsets.UTF_8), 0));

        NopException ex = assertThrows(NopException.class,
                () -> new AiOrmDocumentConverter().convertToText(doc, "orm.xml", DocumentConvertOptions.create()));
        assertUnsupportedConversion(ex, "txt", "orm.xml");
    }

    @Test
    public void testAiXdefUnsupportedConversionRejectedWithErrorCode() {
        IDocumentObject doc = new ResourceDocumentObject("txt",
                new ByteArrayResource("/test.txt", "hello".getBytes(StandardCharsets.UTF_8), 0));

        NopException ex = assertThrows(NopException.class,
                () -> new AiXdefDocumentConverter().convertToText(doc, "ai-xdef.xml", DocumentConvertOptions.create()));
        assertUnsupportedConversion(ex, "txt", "ai-xdef.xml");
    }

    private static void assertUnsupportedConversion(NopException ex, String from, String to) {
        assertEquals(ERR_AI_CODER_UNSUPPORTED_CONVERSION.getErrorCode(), ex.getErrorCode(),
                "unsupported conversion must carry ERR_AI_CODER_UNSUPPORTED_CONVERSION");
        assertEquals(from, ex.getParam(ARG_FROM), "source file type must be attached as a param");
        assertEquals(to, ex.getParam(ARG_TO), "target file type must be attached as a param");
        assertTrue(ex.getMessage().contains("Unsupported conversion:" + from + "->" + to),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testUnknownSqlTypeErrorDescriptionIsEnglish() {
        assertEquals("nop.err.ai.coder.unknown-sql-type", ERR_AI_CODER_UNKNOWN_SQL_TYPE.getErrorCode(),
                "error-code ID contract must stay stable (round 4)");
        String description = ERR_AI_CODER_UNKNOWN_SQL_TYPE.getDescription();
        assertNoCjk(description);
        assertTrue(description.contains("Unknown SQL type"),
                "description must carry the sql-type semantics, actual=" + description);
    }

    @Test
    public void testHeadersAndDataNotMatchErrorDescriptionIsEnglish() {
        assertEquals("nop.err.ai.coder.headers-and-data-not-match",
                ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH.getErrorCode(),
                "error-code ID contract must stay stable (round 4)");
        String description = ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH.getDescription();
        assertNoCjk(description);
        assertTrue(description.contains("Headers and data column count mismatch"),
                "description must carry the headers/data semantics, actual=" + description);
    }

    @Test
    public void testUnknownSqlTypeThrowSiteCarriesEnglishMessage() {
        NopException ex = assertThrows(NopException.class,
                () -> AiOrmSqlType.getStdSqlType("bogus-type"),
                "unknown sql type must fail fast at the throw site");
        assertEquals(ERR_AI_CODER_UNKNOWN_SQL_TYPE.getErrorCode(), ex.getErrorCode(),
                "throw site must keep pointing at ERR_AI_CODER_UNKNOWN_SQL_TYPE");
        assertEquals("bogus-type", ex.getParam(ARG_SQL_TYPE),
                "the unknown sql type must be attached as a param");
        assertTrue(ex.getMessage().contains("Unknown SQL type: bogus-type"),
                "throw-site message must render the English description, actual=" + ex.getMessage());
    }

    @Test
    public void testHeadersAndDataMismatchThrowSiteCarriesEnglishMessage() {
        NopException ex = assertThrows(NopException.class,
                () -> AiCoderHelper.parseList("a,b,c", "1,2", ','),
                "mismatched headers/data must fail fast at the throw site");
        assertEquals(ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH.getErrorCode(), ex.getErrorCode(),
                "throw site must keep pointing at ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH");
        assertEquals("a,b,c", ex.getParam(ARG_HEADERS), "headers must be attached as a param");
        assertEquals("1,2", ex.getParam(ARG_DATA), "data must be attached as a param");
        assertTrue(ex.getMessage().contains("Headers and data column count mismatch"),
                "throw-site message must render the English description, actual=" + ex.getMessage());
    }

    /** 描述文本不得含 CJK 字符（AGENTS.md English error-message 约定，round 4 收口）。 */
    private static void assertNoCjk(String text) {
        assertTrue(text.codePoints().noneMatch(TestCoderErrorCodeConversion::isCjk),
                "description must not contain CJK characters, actual=" + text);
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF);
    }
}
