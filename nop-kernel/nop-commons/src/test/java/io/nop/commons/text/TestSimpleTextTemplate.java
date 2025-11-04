package io.nop.commons.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSimpleTextTemplate {
    @Test
    public void testParse() {
        SimpleTextTemplate template = new SimpleTextTemplate("a{{b}}c{{d}}e");
        assertEquals("a{{b}}c{{d}}e", template.toString());
    }
}
