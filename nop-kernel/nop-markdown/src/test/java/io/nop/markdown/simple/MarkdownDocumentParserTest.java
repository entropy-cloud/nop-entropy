package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.model.MarkdownDocument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDocumentParserTest {

    @Test
    void testParseFrontMatter_Simple() {
        String markdown = "---\n" +
                "title: Test Document\n" +
                "author: Test Author\n" +
                "---\n" +
                "\n" +
                "# Heading\n" +
                "\n" +
                "Content here.";

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        MarkdownDocument doc = parser.parseFromText(null, markdown);

        Map<String, Object> frontMatter = doc.getFrontMatter();
        assertNotNull(frontMatter);
        assertEquals("Test Document", frontMatter.get("title"));
        assertEquals("Test Author", frontMatter.get("author"));

        String generated = doc.toText();
        System.out.println(generated);
    }

    @Test
    void testParseFrontMatter_WithList() {
        String markdown = "---\n" +
                "title: Test Document\n" +
                "tags: [java, test, markdown]\n" +
                "---\n" +
                "\n" +
                "# Heading\n" +
                "\n" +
                "Content here.";

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        MarkdownDocument doc = parser.parseFromText(null, markdown);

        Map<String, Object> frontMatter = doc.getFrontMatter();
        assertNotNull(frontMatter);
        assertEquals("Test Document", frontMatter.get("title"));
    }

    @Test
    void testParseFrontMatter_NoFrontMatter() {
        String markdown = "# Heading\n" +
                "\n" +
                "Content here.";

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        MarkdownDocument doc = parser.parseFromText(null, markdown);

        Map<String, Object> frontMatter = doc.getFrontMatter();
        assertNull(frontMatter);
    }

    @Test
    void testToText_WithFrontMatter() {
        String markdown = "---\n" +
                "title: Test Document\n" +
                "author: Test Author\n" +
                "---\n" +
                "\n" +
                "# Heading\n" +
                "\n" +
                "Content here.";

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        MarkdownDocument doc = parser.parseFromText(null, markdown);

        String generated = doc.toText();
        System.out.println(generated);

        // Check that front matter is included
        assertTrue(generated.startsWith("---\n"));
        assertTrue(generated.contains("title: Test Document"));
        assertTrue(generated.contains("author: Test Author"));
    }

    @Test
    void testCloneInstance_WithFrontMatter() {
        String markdown = "---\n" +
                "title: Test Document\n" +
                "author: Test Author\n" +
                "---\n" +
                "\n" +
                "# Heading\n" +
                "\n" +
                "Content here.";

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        MarkdownDocument doc = parser.parseFromText(null, markdown);

        MarkdownDocument cloned = doc.cloneInstance();

        assertNotNull(cloned.getFrontMatter());
        assertEquals("Test Document", cloned.getFrontMatter().get("title"));
        assertEquals("Test Author", cloned.getFrontMatter().get("author"));

        // Verify independence (cloned front matter should not affect original)
        cloned.getFrontMatter().put("title", "Modified Title");
        assertEquals("Test Document", doc.getFrontMatter().get("title"));
        assertEquals("Modified Title", cloned.getFrontMatter().get("title"));
    }

    @Test
    void testBuildText_WithFrontMatter() {
        String markdown = "---\n" +
                "title: Test Document\n" +
                "author: Test Author\n" +
                "---\n" +
                "\n" +
                "# Heading\n" +
                "\n" +
                "Content here.";

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        MarkdownDocument doc = parser.parseFromText(null, markdown);

        StringBuilder sb = new StringBuilder();
        doc.buildText(sb, false);
        String generated = sb.toString();

        System.out.println(generated);

        // Check that front matter is included
        assertTrue(generated.startsWith("---\n"));
        assertTrue(generated.contains("title: Test Document"));
        assertTrue(generated.contains("author: Test Author"));
    }

    @Test
    void testToText_EmptyFrontMatter() {
        String markdown = "# Heading\n" +
                "\n" +
                "Content here.";

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        MarkdownDocument doc = parser.parseFromText(null, markdown);

        String generated = doc.toText();

        // Should not start with front matter
        assertFalse(generated.startsWith("---\n"));
        // Should contain the heading
        assertTrue(generated.contains("# Heading"));
    }

    private void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true but was false");
        }
    }
}
