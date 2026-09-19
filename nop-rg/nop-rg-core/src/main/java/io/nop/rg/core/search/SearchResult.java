package io.nop.rg.core.search;

import java.util.ArrayList;
import java.util.List;

/**
 * 单文件搜索结果：命中列表（按偏移升序追加）。
 */
public class SearchResult {
    private final List<MatchResult> matches = new ArrayList<>();
    private boolean truncated; // 命中数达到 maxMatches 上限被截断

    public void add(MatchResult match) {
        matches.add(match);
    }

    public List<MatchResult> getMatches() {
        return matches;
    }

    public int getCount() {
        return matches.size();
    }

    public boolean hasMatches() {
        return !matches.isEmpty();
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
