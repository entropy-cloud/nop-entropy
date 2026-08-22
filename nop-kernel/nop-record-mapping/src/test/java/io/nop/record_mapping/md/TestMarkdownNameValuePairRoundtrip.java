package io.nop.record_mapping.md;

import io.nop.commons.util.objects.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestMarkdownNameValuePairRoundtrip {

    /**
     * 暴露生成端的 protected encodeKey，用于验证 encodeKey -> parseNameValuePair 的 roundtrip 对称性
     */
    static class ExposingGenerator extends MappingBasedMarkdownGenerator {
        ExposingGenerator() {
            super(null, null, null);
        }

        @Override
        protected String encodeKey(String str) {
            return super.encodeKey(str);
        }
    }

    private final ExposingGenerator generator = new ExposingGenerator();
    private final MappingBasedMarkdownParser parser = new MappingBasedMarkdownParser(null);

    private void assertRoundtrip(String key, String value) {
        String encoded = generator.encodeKey(key) + ": " + value;
        Pair<String, String> pair = parser.parseNameValuePair(encoded);
        assertEquals(key, pair.getLeft());
        assertEquals(value, pair.getRight());
    }

    @Test
    public void testPlainKeyRoundtrip() {
        assertRoundtrip("plainKey", "v");
    }

    @Test
    public void testKeyWithColonRoundtrip() {
        // 修复前：按第一个 ':'（引号内）切分，key 变成带残留引号的 "a
        assertRoundtrip("a:b", "v");
        assertRoundtrip("a:b:c", "v1");
    }

    @Test
    public void testKeyWithPipeAndQuoteRoundtrip() {
        assertRoundtrip("a|b", "v");
        assertRoundtrip("k\"q", "v");
        assertRoundtrip("k:|\"", "v");
    }

    @Test
    public void testQuotedKeyUnquotedByDecodeKey() {
        // 修复前 decodeKey 不做 unquote，含 : 的键 roundtrip 后键带引号残留
        Pair<String, String> pair = parser.parseNameValuePair(generator.encodeKey("x:y") + ": v");
        assertEquals("x:y", pair.getLeft());
        assertEquals("v", pair.getRight());
    }

    @Test
    public void testUnquotedKeyWithNoColon() {
        Pair<String, String> pair = parser.parseNameValuePair("onlyKey");
        assertEquals("onlyKey", pair.getLeft());
        assertNull(pair.getRight());
    }

    @Test
    public void testMalformedQuotedKeyFallsBack() {
        // 只有起始引号没有结束引号：退回按第一个 ':' 切分的旧行为
        Pair<String, String> pair = parser.parseNameValuePair("\"broken: v");
        assertEquals("\"broken", pair.getLeft());
        assertEquals("v", pair.getRight());
    }
}
