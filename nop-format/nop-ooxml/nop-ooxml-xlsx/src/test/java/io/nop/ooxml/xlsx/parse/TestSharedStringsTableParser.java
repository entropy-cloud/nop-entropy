/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.parse;

import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSharedStringsTableParser {

    @Test
    public void testXmlSpacePreservation() {
        IResource resource = new ClassPathResource("classpath:io/nop/ooxml/xlsx/parse/test-multiline-space.xml");
        SharedStringsTableParser parser = new SharedStringsTableParser(false);
        SharedStringsPart part = parser.parseFromResource(resource);
        List<String> strings = part.getItems();

        assertEquals(4, strings.size());
        assertEquals("Normal text", strings.get(0));

        String textWithSpaces = strings.get(1);
        assertTrue(textWithSpaces.startsWith("  "), "Should preserve leading spaces: " + textWithSpaces);
        assertEquals("  Text with leading spaces", textWithSpaces);

        String multiline = strings.get(2);
        assertTrue(multiline.contains("\n"), "Should contain newlines");
        assertTrue(multiline.contains("  "), "Should preserve spaces after newline");
        String[] lines = multiline.split("\n");
        assertEquals(3, lines.length);
        assertEquals("Line1", lines[0]);
        assertEquals("  Line2 with indent", lines[1]);
        assertEquals("    Line3 with more indent", lines[2]);

        String runText = strings.get(3);
        assertEquals("First   Second with spaces", runText);
        assertTrue(runText.startsWith("First"), "Should contain First");
        assertTrue(runText.contains("  Second with spaces"), "Should preserve leading spaces from second run");
    }
}
