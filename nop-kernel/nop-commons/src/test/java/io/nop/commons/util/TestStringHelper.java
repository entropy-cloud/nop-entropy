/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import com.google.common.hash.Hashing;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestStringHelper {
    static final Logger LOG = LoggerFactory.getLogger(TestStringHelper.class);

    @Test
    public void testCamelCaseStartsWithSlash() {
        String str = StringHelper.camelCase("_a_b", false);
        assertEquals("_aB", str);
        assertEquals("_a_b", StringHelper.camelCaseToUnderscore(str, true));
    }

    @Test
    public void testColCodeToPropName() {
        String colCode = "a_b_c";
        String propName = StringHelper.colCodeToPropName(colCode);
        assertEquals("ABC", propName);

        assertEquals("testData", StringHelper.colCodeToPropName("test_data"));
    }

    @Test
    public void testDupEscape() {
        assertEquals("a~~b", StringHelper.encodeDupEscape("a~b", '~'));
        assertEquals("a~b", StringHelper.decodeDupEscape("a~~b", '~'));
        assertEquals("a~~", StringHelper.encodeDupEscape("a~", '~'));
        assertEquals("a~", StringHelper.decodeDupEscape("a~~", '~'));
        assertEquals("~~b", StringHelper.encodeDupEscape("~b", '~'));
        assertEquals("~b", StringHelper.decodeDupEscape("~~b", '~'));
        assertEquals("~~", StringHelper.encodeDupEscape("~", '~'));
        assertEquals("~", StringHelper.decodeDupEscape("~~", '~'));

        assertEquals(Arrays.asList("a", "b"), StringHelper.splitDupEscaped("a~b", '~'));
        assertEquals(Arrays.asList("a~b", "c"), StringHelper.splitDupEscaped("a~~b~c", '~'));
        assertEquals(Arrays.asList("a~", "b"), StringHelper.splitDupEscaped("a~~~b", '~'));
        assertEquals(Arrays.asList("a~", ""), StringHelper.splitDupEscaped("a~~~", '~'));
        assertEquals(Arrays.asList("a~"), StringHelper.splitDupEscaped("a~~", '~'));
        assertEquals(Arrays.asList("", ""), StringHelper.splitDupEscaped("~", '~'));
        assertEquals(Arrays.asList("~"), StringHelper.splitDupEscaped("~~", '~'));
    }

    @Test
    public void testWhitespace() {
        assertTrue(Character.isWhitespace('\n'));
    }

    @Test
    public void testUrlPath() {
        File file = new File(".").getAbsoluteFile();
        URI url = file.toURI();
        System.out.println(url);
    }

    String escapeTo(String s) {
        StringBuilder sb = new StringBuilder();
        try {
            StringHelper.escapeJsonTo(s, sb);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return sb.toString();
    }

    @Test
    public void testToUnderscore() {
        assertEquals("a_b", StringHelper.camelCaseToUnderscore("aB", true));
        assertEquals("A_B", StringHelper.camelCaseToUnderscore("aB", false));

        assertEquals("A_B", StringHelper.camelCaseToUnderscore("AB", false));
        assertEquals("a_b", StringHelper.camelCaseToUnderscore("AB", true));
    }

    @Test
    public void testToHyphen() {
        assertEquals("a-b", StringHelper.camelCaseToHyphen("AB"));
    }

    @Test
    public void testSplit() {
        String[] s = "a-b".split("-");
        assertEquals("a", s[0]);
    }

    @Test
    public void testEscapeTo() {

        String ss = StringHelper.repeat("a1\n2", 100);
        assertEquals(StringHelper.replace(ss, "\n", "\\n"), escapeTo(ss));

        assertEquals("\\\"", escapeTo("\""));
    }

    @Test
    public void testIsNumber() {
        assertFalse(StringHelper.isNumber("3m"));
        assertTrue(StringHelper.isNumber("0"));
        assertTrue(StringHelper.isNumber("3."));
        assertTrue(StringHelper.isNumber(".3"));
        assertTrue(StringHelper.isNumber("0E5"));
        assertTrue(StringHelper.isNumber("-2.0"));
        assertTrue(StringHelper.isNumber("0x5"));
        assertTrue(StringHelper.isNumber("0xa"));
        assertFalse(StringHelper.isNumber("0xaK"));
        assertTrue(StringHelper.isNumber("1233"));
        assertTrue(StringHelper.isNumber("-13.445"));
        assertTrue(StringHelper.isNumber("-13.445E3"));
        assertTrue(StringHelper.isNumber("-13.445E-3"));
        assertFalse(StringHelper.isNumber("-13.445E-"));
        assertFalse(StringHelper.isNumber("023"));

        try {
            Double.parseDouble("-34.3E3");
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Test
    public void testFileExt() {
        assertEquals("abc", StringHelper.removeFileExt("abc.txt"));
        assertEquals("abc.b", StringHelper.removeFileExt("abc.b.d"));
        assertEquals("/abc/e", StringHelper.removeFileExt("/abc/e.vue"));
        assertEquals("/abc/e.js", StringHelper.replaceFileExt("/abc/e.vue", "js"));
    }

    @Test
    public void testFileType() {
        assertEquals("abc", StringHelper.fileType("/ab/a.abc"));
        assertEquals("xml", StringHelper.fileType("abc.xml"));
        assertEquals("abc.xml", StringHelper.fileType("x/x.abc.xml"));
        assertEquals("/abc", StringHelper.removeFileType("/abc.vue.xml"));
        assertEquals("/abc/e.vue", StringHelper.replaceFileType("/abc/e.page.xml", "vue"));
    }

    @Test
    public void testNumber() {
        assertEquals(2L, StringHelper.parseNumber("2L"));
        assertEquals(2L, StringHelper.parseNumber("2L"));
        assertEquals(2.0, StringHelper.parseNumber("2.0D"));
        assertEquals(2.0F, StringHelper.parseNumber("2.0F"));
        assertEquals(21, StringHelper.parseNumber("21"));
        assertEquals(212L, StringHelper.parseNumber("212L"));

        try {
            StringHelper.parseNumber("212 L");
            fail();
        } catch (Exception e) {
            LOG.debug("error", e);
        }
    }

    @Test
    public void testFileSizeString() {
        assertEquals("10K", StringHelper.fileSizeString(10240));
        assertEquals("10.9K", StringHelper.fileSizeString(11249));
        assertEquals("10M", StringHelper.fileSizeString(1024 * 1024 * 10));
        assertEquals("1G", StringHelper.fileSizeString(1024 * 1024 * 1024));
    }

    @Test
    public void testVar() {
        assertTrue(StringHelper.isValidJavaVarName("pdmModelHelper"));
    }

    @Test
    public void testHex() {
        String s = "sj;sdufp43rn;lfd8-w4j5nm;sdp8i45/ma8F*F";
        String hex = StringHelper.bytesToHex(s.getBytes(StringHelper.CHARSET_UTF8));
        String s2 = new String(StringHelper.hexToBytes(hex), StringHelper.CHARSET_UTF8);
        assertEquals(s, s2);
    }

    @Test
    public void testCommon() {
        assertNull(StringHelper.strip(null));
        assertEquals("ab", StringHelper.strip(" ab "));
        assertNull(StringHelper.strip("   "));
        assertNull(StringHelper.strip(""));
        assertNull(StringHelper.strip(" "));
        assertEquals("a", StringHelper.strip("a"));
        assertEquals("a", StringHelper.strip("  a"));
        assertEquals("a", StringHelper.strip("a   "));

        assertTrue(StringHelper.startsWithIgnoreCase("abCd", "abc"));
        assertFalse(StringHelper.startsWithIgnoreCase("a", "abc"));
        assertTrue(StringHelper.startsWithIgnoreCase("abc", ""));
        assertFalse(StringHelper.startsWithIgnoreCase("", "abc"));

        assertTrue(StringHelper.endsWithIgnoreCase("abcd", "Cd"));
        assertFalse(StringHelper.endsWithIgnoreCase("abcd", "dc"));
        assertFalse(StringHelper.endsWithIgnoreCase("a", "abc"));

        assertEquals("\r\n\r\n\r\na", StringHelper.removeHtmlTag("<p><br><br/>a</p>"));

        assertEquals("ab...", StringHelper.limitLen("abcdef", 5));
        assertEquals("...bc...", StringHelper.limitLen("1234abcdefg", 5, 8));

        assertEquals("[a, b]", StringHelper.split("a,b", ',').toString());
        assertEquals("[a]", StringHelper.split("a", ',').toString());
        assertEquals("[]", StringHelper.split("", ',').toString());

        assertEquals("001", StringHelper.leftPad("1", 3, '0'));
        assertEquals("20", StringHelper.rightPad("2", 2, '0'));

        assertEquals("a2b", StringHelper.replace("axxxb", "xxx", "2"));
        assertEquals("2b", StringHelper.replace("xb", "x", "2"));
        assertEquals("a2", StringHelper.replace("axx", "xx", "2"));
        assertEquals("ab", StringHelper.replace("axxb", "xx", ""));
        assertEquals("a2b2c", StringHelper.replace("axxbxxc", "xx", "2"));
        assertEquals("a22c", StringHelper.replace("axxxxc", "xx", "2"));

        assertEquals("[]", StringHelper.stripedSplit(" , ", ',').toString());
        assertEquals("[]", StringHelper.stripedSplit(" ,", ',').toString());
        assertEquals("321d", StringHelper.replaceChars("abcd", "cba", "123"));

        assertEquals("abc&1233", new String(StringHelper.hexToBytes(StringHelper.bytesToHex("abc&1233".getBytes()))));

        assertEquals(3 + 1 / 60.0 + 2 / 3600.0,
                StringHelper.parseDegree(StringHelper.formatDegree(3 + 1 / 60.0 + 2 / 3600.0)));
    }

    @Test
    public void testPath() {
        assertEquals("/a/b/f", StringHelper.normalizePath("/a/b/c/../f"));
        assertEquals("/a/b/f/", StringHelper.normalizePath("/a/b/c/../f/"));
        assertEquals("/a/b/c/f/", StringHelper.normalizePath("/a/b/c/./f/"));
        assertEquals("/a/b/c/..x/f/", StringHelper.normalizePath("/a/b/c/..x/f/"));
        assertEquals("/a/b", StringHelper.normalizePath("/../../a/b"));

        assertEquals("e/", StringHelper.relativizePath("/a/b/c", "/a/b/e/"));
        assertEquals("../e/", StringHelper.relativizePath("/a/b/c", "/a/e/"));

        assertEquals("/a/b.jsp", StringHelper.absolutePath("/a/c.jsp", "b.jsp"));
        assertEquals("/a/b.jsp", StringHelper.absolutePath("/a/x/c.jsp", "../b.jsp"));
        assertEquals("/z/b.jsp", StringHelper.absolutePath("/a/x/c.jsp", "/z/b.jsp"));
    }

    @Test
    public void testEscape() {
        assertEquals("\\r\\n\\\"\\'", StringHelper.escapeJava("\r\n\"'"));
        assertEquals("ab'\\\"\\r\\n", StringHelper.escapeJson("ab'\"\r\n"));
        assertEquals("&lt;&gt;&quot;&apos;&amp;abc", StringHelper.escapeXml("<>\"'&abc"));
        assertEquals("&lt;&gt;\"'&amp;abc", StringHelper.escapeXmlValue("<>\"'&abc"));
        assertEquals("&lt;&gt;&quot;'&amp;abc", StringHelper.escapeXmlAttr("<>\"'&abc"));
        assertEquals("<br/>&lt;", StringHelper.escapeHtml("\r\r\n<"));
    }

    @Test
    public void testQuery() {
        assertEquals("{a=1, b=2}", StringHelper.parseQuery("a=1&b=2&", null).toString());
        assertEquals("{a=1, b=2}", StringHelper.parseQuery("a=1&&b=2", null).toString());
        assertEquals("{a=1, b=}", StringHelper.parseQuery("a=1&&b", null).toString());
    }

    @Test
    public void testNumber2() {
        assertEquals(-1.0, StringHelper.parseNumber("-1.0"));
        assertEquals(-2, StringHelper.parseNumber("-2"));
    }

    @Test
    public void testXml() {
        assertEquals("null", StringHelper.escapeXmlAttr("null"));
        String xml = "&amp;&quot;&lt;&gt;&#160;";
        String s = StringHelper.unescapeXml(xml);
        assertEquals(5, s.length());
        assertEquals(xml, StringHelper.escapeXml(s));
    }

    @Test
    public void testAbsolutePath() {
        assertEquals("file:/test.txt", StringHelper.normalizePath("file:/test.txt"));
        assertEquals("file:../test.txt", StringHelper.normalizePath("file:../test.txt"));
        assertEquals("file:/test.txt", StringHelper.normalizePath("file:/a/../test.txt"));
        assertEquals("file:test.txt", StringHelper.absolutePath("/a/b.txt", "file:test.txt"));
        assertEquals("file:test.txt", StringHelper.absolutePath("file:/a/b.txt", "file:test.txt"));
        assertEquals("file:/a/test.txt", StringHelper.absolutePath("file:/a/b.txt", "test.txt"));
        assertEquals("/test.txt", StringHelper.absolutePath("file:/a/b.txt", "/test.txt"));
        assertEquals("file:a/test.txt", StringHelper.absolutePath("file:a/b.txt", "test.txt"));
        assertEquals("classpath:dao.beans.xml",
                StringHelper.absolutePath("classpath:bootstrap.beans.xml", "dao.beans.xml"));
    }

    @Test
    public void testMatch() {
        assertTrue(StringHelper.matchSimplePattern("a", "a*"));
        assertTrue(StringHelper.matchSimplePattern("a", "*a*"));
        assertTrue(StringHelper.matchSimplePattern("a", "*a"));
        assertTrue(StringHelper.matchSimplePattern("a", "a"));
        assertTrue(StringHelper.matchSimplePattern("abc", "a*"));
        assertTrue(StringHelper.matchSimplePattern("abc", "ab*"));
        assertTrue(StringHelper.matchSimplePattern("abc", "*b*"));
        assertTrue(StringHelper.matchSimplePattern("abc", "*bc"));
        assertTrue(StringHelper.matchSimplePattern("abc", "*c"));
        assertFalse(StringHelper.matchSimplePattern("a", "ab*"));
        assertFalse(StringHelper.matchSimplePattern("abc", "abcd"));
        assertFalse(StringHelper.matchSimplePattern("abc", "b*"));
        assertFalse(StringHelper.matchSimplePattern("abc", "*ac*"));
        assertTrue(StringHelper.matchSimplePattern("abc", "**"));
    }

    @Test
    public void testEscape2() {
        String s = "a\\/";
        assertEquals("a/", StringHelper.unescapeJava(s));
        assertEquals("a/", StringHelper.unescapeJson(s));

        assertEquals("/", StringHelper.escapeJson("/"));
    }

    @Test
    public void testCamelCase() {
        assertEquals("a", StringHelper.camelCase("A", false));
        assertEquals("aB", StringHelper.camelCase("A_B", false));
        assertEquals("_b", StringHelper.camelCase("_B", false));
        assertEquals("A", StringHelper.camelCase("A", true));
        assertEquals("AB", StringHelper.camelCase("A_B", true));
        assertEquals("_b", StringHelper.camelCase("_B", true));
    }

    @Test
    public void testFileName() throws IOException {
        char c = 127;
        File file = new File(c + "");
        file.createNewFile();
        assertTrue(file.exists());
        file.delete();
    }

    @Test
    public void testXmlName() {
        assertTrue(!StringHelper.isXmlNamePart('>'));
        assertTrue(!StringHelper.isXmlNamePart('<'));
        assertTrue(StringHelper.isValidXmlName("v-on:click", true, true));
        assertTrue(StringHelper.isValidHtmlAttrName("v-on:click"));
    }

    @Test
    public void testFormatNumber() {
        System.out.println(0.00005);
        assertEquals("5.0E-5", String.valueOf(0.00005));

        String s = StringHelper.formatNumber(0.0005, "#.#############");
        assertEquals("0.0005", s);

        assertEquals("10000000.00005", StringHelper.formatNumber(10000000.00005));
    }

    @Test
    public void testEnvToConfigVar() {
        String var = "nop.data.a-b-c.d_b-c";
        String env = "NOP_DATA_A__B__C_D___B__C";
        assertEquals(var, StringHelper.envToConfigVar(env));
        assertEquals(env, StringHelper.configVarToEnv(var));
    }

    @Test
    public void testConfigVar() {
        String configVar = "nop.test.testDataOther.other";

        configVar = StringHelper.normalizeConfigVar(configVar);
        assertEquals("nop.test.test-data-other.other", configVar);
        assertTrue(StringHelper.isValidConfigVar(configVar));

        assertFalse(StringHelper.isValidConfigVar("a..b"));
        assertFalse(StringHelper.isValidConfigVar("3a"));
        assertTrue(StringHelper.isValidConfigVar("a.2.3"));
        assertFalse(StringHelper.isValidConfigVar("a.2-test.3"));
        assertFalse(StringHelper.isValidConfigVar("a.a--t"));
        assertFalse(StringHelper.isValidConfigVar("b..at"));
    }

    @Test
    public void testRenderTemplate() {
        String tpl = "daa:{a},{b}";
        String result = StringHelper.renderTemplate(tpl, key -> key + '1');
        assertEquals("daa:a1,b1", result);

        result = StringHelper.renderTemplate("abc", key -> key + 'a');
        assertEquals("abc", result);

        result = StringHelper.renderTemplate("{x}", key -> key + 's');
        assertEquals("xs", result);
    }

    @Test
    public void testEscapeCRLF() {
        assertEquals("\r\n\r\n\r\n", StringHelper.normalizeCRLF("\n\n\n", true));
        assertEquals("\n\n\n", StringHelper.normalizeCRLF("\n\n\n", false));
        assertEquals("\r\n\r\n\r\n", StringHelper.normalizeCRLF("\r\n\r\n\r\n", true));

        assertEquals("a\n\nb", StringHelper.normalizeCRLF("a\r\r\nb", false));

        assertEquals("\n\n\n", StringHelper.normalizeCRLF("\r\n\n\r\n", false));
        assertEquals("\r\n\r\n\r\n", StringHelper.normalizeCRLF("\r\r\n\n", true));
        assertEquals("&#10;&#10;", StringHelper.escapeCRLF("\r\n\r\n", "&#10;"));

        assertEquals("\n\n", StringHelper.normalizeCRLF("\n\r", false));
        assertEquals("\n\n", StringHelper.normalizeCRLF("\r\r", false));
        assertEquals("\r\n\r\n", StringHelper.normalizeCRLF("\r\r", true));
        assertEquals("\r\n\r\n", StringHelper.normalizeCRLF("\r\n\r", true));
        assertEquals("\r\n", StringHelper.normalizeCRLF("\r", true));
        assertEquals("\r\n", StringHelper.normalizeCRLF("\n", true));
        assertEquals("\r\n", StringHelper.normalizeCRLF("\r\n", true));
    }

    @Test
    public void testNormalizePath() {
        assertEquals("/a/b", StringHelper.normalizePath("\\a\\./x/../b"));
        assertEquals("/a/", StringHelper.normalizePath("\\a\\./x/../"));
        assertEquals("a/b", StringHelper.normalizePath(".\\a\\./x/../b"));
        assertEquals("a/b", StringHelper.normalizePath("..\\a\\./x/../b"));

        assertEquals("c:/a/b.txt", StringHelper.normalizePath("c:\\a\\x\\..\\b.txt"));
    }

    @Test
    public void testUUID() {
        System.out.println(StringHelper.generateUUID());
    }

    @Test
    public void testDoubleEscape() {
        String str = "a`b`";
        String q = StringHelper.quoteDupEscapeString(str, '`');
        assertEquals(q, "`a``b```");
        assertEquals(str, StringHelper.unquoteDupEscapeString(q));

        str = "a`b";
        q = StringHelper.quoteDupEscapeString(str, '`');
        assertEquals(q, "`a``b`");
        assertEquals(str, StringHelper.unquoteDupEscapeString(q));
    }

    @Test
    public void testSha256() {
        String key = "abc";
        String str = "xyz";
        String digest = StringHelper.hmacSha256(str, key);
        String digest2 = StringHelper.bytesToHex(Hashing.hmacSha256(key.getBytes(StringHelper.CHARSET_UTF8))
                .hashString(str, StringHelper.CHARSET_UTF8).asBytes());

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StringHelper.CHARSET_UTF8), "HmacSHA256"));
            byte[] hash = mac.doFinal(str.getBytes(StringHelper.CHARSET_UTF8));
            System.out.println(StringHelper.bytesToHex(hash));
        } catch (Exception e) {

        }
        assertEquals(digest, digest2);
        assertEquals(digest, StringHelper.hmacSha256(str, key));
    }

    @Test
    public void testQuotedString() {
        assertTrue(StringHelper.isQuotedString("'sss'"));
        assertTrue(StringHelper.isQuotedString("\"sss'\""));
        assertFalse(StringHelper.isQuotedString("\"sss'"));
    }

    @Test
    public void testNextName() {
        assertEquals("1", StringHelper.nextName(""));
        assertEquals("abc1", StringHelper.nextName("abc"));
        assertEquals("a2", StringHelper.nextName("a1"));
        assertEquals("a9", StringHelper.nextName("a8"));
        assertEquals("a10", StringHelper.nextName("a9"));
        assertEquals("a100", StringHelper.nextName("a99"));
        assertEquals("100", StringHelper.nextName("99"));
        assertEquals("99", StringHelper.nextName("98"));
        assertEquals("[1]", StringHelper.nextName("[0]"));
        assertEquals("a(2)", StringHelper.nextName("a(1)"));
    }

    @Test
    public void testUtf8Len() {
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(0x1F431);
        sb.appendCodePoint(0x00E9);
        sb.append('中');
        String str = sb.toString();

        assertEquals("", StringHelper.limitUtf8Len(str, 1));
        assertEquals("", StringHelper.limitUtf8Len(str, 2));
        assertEquals("", StringHelper.limitUtf8Len(str, 3));
        assertEquals(0x1F431, StringHelper.limitUtf8Len(str, 4).codePointAt(0));
        assertEquals(0x1F431, StringHelper.limitUtf8Len(str, 5).codePointAt(0));

        assertEquals(0xE9, StringHelper.limitUtf8Len(str, 6).charAt(2));
        assertEquals(3, StringHelper.limitUtf8Len(str, 7).length());
        assertEquals(3, StringHelper.limitUtf8Len(str, 8).length());
        assertEquals(4, StringHelper.limitUtf8Len(str, 9).length());
    }

    @Test
    public void testStringMap() {
        String str = "a=b\r\n c = d, f=g\n\r\n,";

        Map<String, String> map = StringHelper.parseStringMap(str);
        assertEquals(3, map.size());
        assertEquals("b", map.get("a"));
        assertEquals("d", map.get("c"));
        assertEquals("g", map.get("f"));
    }

    @Test
    public void testStringFormat() {
        String str = MessageFormat.format("{0} a", "DEBUG");
        System.out.println(str);
        assertEquals("DEBUG a", str);
    }

    @Test
    public void testUnescapeMarkdown() {
        String text = "\\|";
        assertEquals("|", StringHelper.unescapeMarkdown(text));
    }

    @Test
    public void testReverseMappingName() {
        String text = "a.b.c.X_to_yy";
        assertEquals("a.b.c.yy_to_X", StringHelper.reverseMappingName(text));
    }
}