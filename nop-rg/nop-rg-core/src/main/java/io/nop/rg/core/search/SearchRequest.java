package io.nop.rg.core.search;

/**
 * 一次内容搜索请求：字面量字节模式 + 命中数上限。
 */
public class SearchRequest {
    private final byte[] pattern;
    private final int maxMatches; // 0 表示不限制

    public SearchRequest(byte[] pattern, int maxMatches) {
        if (pattern == null || pattern.length == 0) {
            throw new IllegalArgumentException("search pattern must not be empty");
        }
        if (maxMatches < 0) {
            throw new IllegalArgumentException("maxMatches must not be negative");
        }
        this.pattern = pattern.clone();
        this.maxMatches = maxMatches;
    }

    public static SearchRequest of(byte[] pattern) {
        return new SearchRequest(pattern, 0);
    }

    public byte[] getPattern() {
        return pattern.clone();
    }

    public int patternLength() {
        return pattern.length;
    }

    public int getMaxMatches() {
        return maxMatches;
    }

    public boolean hasMore(int currentCount) {
        return maxMatches <= 0 || currentCount < maxMatches;
    }
}
