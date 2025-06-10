package io.nop.idea.plugin.utils;

import java.util.HashMap;
import java.util.Map;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Assert;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-10
 */
public class TestMarkdownHelper extends LightJavaCodeInsightFixtureTestCase {

    public void testRenderHtml() {
        Map<String, String> samples = new HashMap<>() {{
            put("[Abc](https://a.b.c/abc)",
                "<p><a href=\"https://a.b.c/abc\" />{Link} Abc <code>https://a.b.c/abc</code></a></p>");
            put("[Abc](https://a.b.c/abc \"Title\")",
                "<p><a href=\"https://a.b.c/abc\" />{Link} Abc <code>https://a.b.c/abc</code></a></p>");
            put("[](https://a.b.c/abc \"Title\")",
                "<p><a href=\"https://a.b.c/abc\" />{Link} Title <code>https://a.b.c/abc</code></a></p>");
            put("![Abc](https://a.b.c/abc)",
                "<p><a href=\"https://a.b.c/abc\" />{Image} Abc <code>https://a.b.c/abc</code></a></p>");
            put("![Abc](https://a.b.c/abc \"Title\")",
                "<p><a href=\"https://a.b.c/abc\" />{Image} Abc <code>https://a.b.c/abc</code></a></p>");
            put("![](https://a.b.c/abc \"Title\")",
                "<p><a href=\"https://a.b.c/abc\" />{Image} Title <code>https://a.b.c/abc</code></a></p>");
            put("https://a.b.c/a/b/c", "<p>https://a.b.c/a/b/c</p>");
        }};

        samples.forEach((text, expected) -> {
            String actual = MarkdownHelper.renderHtml(text).trim();

            System.out.println(actual);
            Assert.assertEquals(expected, actual);
        });
    }
}
