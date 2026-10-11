package io.nop.ai.core.prompt;

import io.nop.api.core.beans.ErrorBean;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the WI1 response facade: the script-facing surface
 * (outputs / validate / resultValue) and the content-parse methods carried
 * over from the legacy chat stack must behave equivalently on the new
 * {@code ChatResponse} kernel.
 */
public class TestChatExchangeFacade {

    private ChatExchange newExchange(String content) {
        ChatExchange exchange = new ChatExchange();
        exchange.setContent(content);
        return exchange;
    }

    @Test
    public void testContentAccessors() {
        ChatExchange exchange = newExchange("# Title\n\nbody");
        assertEquals("# Title\n\nbody", exchange.getContent());
        assertFalse(exchange.isEmpty());
        assertTrue(exchange.isValid());

        ChatExchange empty = new ChatExchange();
        assertNull(empty.getContent());
        assertTrue(empty.isEmpty());
    }

    @Test
    public void testOutputsAndResultValue() {
        ChatExchange exchange = newExchange("text");
        assertNull(exchange.getOutput("noChange"));
        exchange.setOutput("noChange", Boolean.TRUE);
        exchange.setOutput("RESULT", "done");

        assertEquals(Boolean.TRUE, exchange.getOutputs().get("noChange"));
        assertEquals("done", exchange.getResultValue());
        assertEquals("done", exchange.getResultText());
        // script surface: chatExchange.outputs.noChange
        assertEquals(Boolean.TRUE, exchange.getOutputs().get("noChange"));
    }

    @Test
    public void testParseJsonAndYaml() {
        ChatExchange json = newExchange("{\"a\":1}");
        Object parsed = json.parseJsonContent();
        assertNotNull(parsed);

        ChatExchange yaml = newExchange("preamble\n```yaml\n{\"a\": 1, \"b\": \"two\"}\n```\n");
        Map<String, Object> yamlMap = yaml.parseYamlContent();
        assertEquals(1, yamlMap.get("a"));
        assertEquals("two", yamlMap.get("b"));

        ChatExchange blank = new ChatExchange();
        blank.setContent("   ");
        assertNull(blank.parseJsonContent());
    }

    @Test
    public void testBlockExtraction() {
        ChatExchange exchange = newExchange("before\n###BEGIN###\ninner\n###END###\nafter");
        String block = exchange.getBlock("###BEGIN###", "###END###", false, false);
        assertEquals("inner", block.trim());
        assertTrue(exchange.isValid());
    }

    @Test
    public void testBlockMissingMarksInvalid() {
        ChatExchange exchange = newExchange("no markers here");
        String block = exchange.getBlock("###BEGIN###", "###END###", false, false);
        assertNull(block);
        assertTrue(exchange.isInvalid());
        assertNotNull(exchange.getInvalidReason());

        // validate() must rebuild the recorded failure
        assertThrows(Exception.class, exchange::validate);
    }

    @Test
    public void testValidateThrowsOnExplicitInvalid() {
        ChatExchange exchange = newExchange("text");
        exchange.setInvalid(true);
        exchange.setInvalidReason(new ErrorBean("nop.err.ai.test"));
        assertThrows(Exception.class, exchange::validate);
    }

    @Test
    public void testContentContains() {
        assertTrue(newExchange("has [NO_CHANGE] tag").contentContains("[NO_CHANGE]"));
        assertFalse(newExchange("nothing").contentContains("[NO_CHANGE]"));
        assertFalse(new ChatExchange().contentContains("x"));
    }
}
