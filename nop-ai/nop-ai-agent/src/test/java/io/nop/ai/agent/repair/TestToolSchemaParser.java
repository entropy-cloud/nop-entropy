package io.nop.ai.agent.repair;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ToolSchemaParser}. Verifies that real-tool XDEF
 * attribute-notation schemas parse to a non-null parameter map. This proves
 * {@code ToolSchemaConverter} cannot be reused (it returns null for all real
 * tools) — the new parser is the working alternative.
 */
public class TestToolSchemaParser {

    private static XNode parseSchema(String xml) {
        return XNodeParser.instance().parseFromText(SourceLocation.fromPath("[test]"), xml);
    }

    @Test
    void parsesAttributeNotationSchema() {
        XNode schema = parseSchema("<schema>"
                + "<read-file id=\"!int\" explanation=\"!string\" "
                + "path=\"!full-path\" fromLine=\"int\" toLine=\"int\" lastLines=\"int\"/>"
                + "</schema>");

        Map<String, String> params = ToolSchemaParser.parseParameterTypes(schema);

        assertNotNull(params);
        assertFalse(params.isEmpty());
        assertEquals("int", params.get("id"));
        assertEquals("string", params.get("explanation"));
        assertEquals("full-path", params.get("path"));
        assertEquals("int", params.get("fromLine"));
        assertEquals("int", params.get("toLine"));
        assertEquals("int", params.get("lastLines"));
    }

    @Test
    void parsesBooleanAndEnumTypes() {
        XNode schema = parseSchema("<schema>"
                + "<grep id=\"!int\" explanation=\"!string\" timeoutMs=\"int\" "
                + "pattern=\"!string\" path=\"!string\" recursive=\"boolean\" ignoreCase=\"boolean\"/>"
                + "</schema>");

        Map<String, String> params = ToolSchemaParser.parseParameterTypes(schema);

        assertEquals("boolean", params.get("recursive"));
        assertEquals("boolean", params.get("ignoreCase"));
        assertEquals("string", params.get("pattern"));
    }

    @Test
    void parsesChildElementBodyTextAsType() {
        XNode schema = parseSchema("<schema xmlns:xdef=\"/nop/schema/xdef.xdef\">"
                + "<bash id=\"!int\" explanation=\"!string\" timeoutMs=\"int\" workingDir=\"full-path\">"
                + "<command>!string</command>"
                + "<envs xdef:body-type=\"list\" xdef:key-attr=\"name\">"
                + "<env name=\"!string\" value=\"string\" />"
                + "</envs>"
                + "</bash>"
                + "</schema>");

        Map<String, String> params = ToolSchemaParser.parseParameterTypes(schema);

        assertEquals("int", params.get("id"));
        assertEquals("string", params.get("explanation"));
        assertEquals("int", params.get("timeoutMs"));
        assertEquals("full-path", params.get("workingDir"));
        assertEquals("string", params.get("command"));
        assertEquals("list", params.get("envs"));
    }

    @Test
    void parsesEnumAttributeType() {
        XNode schema = parseSchema("<schema xmlns:xdef=\"/nop/schema/xdef.xdef\">"
                + "<update-todos id=\"!int\" explanation=\"!string\">"
                + "<todos xdef:body-type=\"list\">"
                + "<todo content=\"!string\" status=\"!enum:pending|in_progress|completed\"/>"
                + "</todos>"
                + "</update-todos>"
                + "</schema>");

        Map<String, String> params = ToolSchemaParser.parseParameterTypes(schema);

        assertEquals("int", params.get("id"));
        assertEquals("string", params.get("explanation"));
        assertEquals("list", params.get("todos"));
    }

    @Test
    void returnsEmptyMapForNullSchema() {
        Map<String, String> params = ToolSchemaParser.parseParameterTypes(null);
        assertNotNull(params);
        assertTrue(params.isEmpty());
    }

    @Test
    void returnsEmptyMapForSchemaWithNoChildren() {
        XNode schema = parseSchema("<schema></schema>");
        Map<String, String> params = ToolSchemaParser.parseParameterTypes(schema);
        assertNotNull(params);
        assertTrue(params.isEmpty());
    }

    @Test
    void stripsRequiredMarker() {
        XNode schema = parseSchema("<schema>"
                + "<tool required_param=\"!int\" optional_param=\"string\"/>"
                + "</schema>");

        Map<String, String> params = ToolSchemaParser.parseParameterTypes(schema);

        assertEquals("int", params.get("required_param"));
        assertEquals("string", params.get("optional_param"));
    }
}
