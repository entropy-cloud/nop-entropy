package io.nop.find.core.score;

import io.nop.find.core.NopFindException;

import java.util.Locale;

/**
 * 三级模糊打分器（nop-find design 决策 3）：query 与 name/relativePath/path 三个字段分别打分，
 * 取最小者（分越低越优），字段偏移 name +0 / relativePath +25 / path +300。
 *
 * <p>单字段三级打分（分越低越优）：
 * <pre>
 *   text 以 query 开头（前缀档）    → len(text) - len(query)
 *   text 包含 query（子串档）      → 100 + indexOf(query)
 *   query 按序散布于 text（子序列档）→ 200 + Σ(相邻字符间隔) + (len(text) - len(query))
 *   否则                          → 不匹配
 * </pre>
 *
 * <p>子序列匹配取贪心最左匹配位置 p(0..m-1)，相邻间隔为 {@code p(i+1) - p(i) - 1}。
 * 间隔越大命中越松散、分数越高；越紧凑的命中越靠前。
 *
 * <p>大小写：默认不敏感。query 在构造时一次性预归一化（{@link #normalize(String)} 语义），
 * 打分热路径只做字符比较，不重复折叠。候选文本由调用方在遍历阶段预归一化后经
 * {@link #scoreNormalized(String, String, String)} 传入；{@link #score(String, String, String)}
 * 是自动归一化的便捷入口。
 *
 * <p>本类无可变共享状态，可安全并行使用。
 */
public class FuzzyFileScorer {

    /** 不匹配哨兵值。 */
    public static final int NO_MATCH = Integer.MAX_VALUE;

    /** 子串档基准分（低于此为前缀档）。 */
    public static final int SUBSTRING_BASE = 100;
    /** 子序列档基准分（低于此为前缀/子串档）。 */
    public static final int SUBSEQUENCE_BASE = 200;

    /** 三路字段偏移：文件名最优。 */
    public static final int NAME_OFFSET = 0;
    /** 三路字段偏移：相对路径次之（覆盖跨目录段 query）。 */
    public static final int RELATIVE_PATH_OFFSET = 25;
    /** 三路字段偏移：完整路径兜底（保证绝对路径型 query 仍可命中）。 */
    public static final int PATH_OFFSET = 300;

    private final String query;
    private final boolean caseSensitive;

    public FuzzyFileScorer(String query, boolean caseSensitive) {
        if (query == null || query.isEmpty()) {
            throw new NopFindException("find query must not be empty");
        }
        this.caseSensitive = caseSensitive;
        this.query = caseSensitive ? query : normalize(query);
    }

    /**
     * 预归一化：大小写不敏感时按 {@link Locale#ROOT} 小写折叠（避免土耳其语 I 等 locale 陷阱）。
     * 已是小写的文本原样返回（零分配短路）。
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (indexOfUpperCase(text) < 0) {
            return text; // 无大写：零分配短路
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static int indexOfUpperCase(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return i;
            }
        }
        return -1;
    }

    public String getQuery() {
        return query;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * 打分（自动归一化入口）。返回 {@link #NO_MATCH} 表示无任何字段命中。
     */
    public int score(String name, String relativePath, String absolutePath) {
        if (caseSensitive) {
            return scoreNormalized(name, relativePath, absolutePath);
        }
        return scoreNormalized(normalize(name), normalize(relativePath), normalize(absolutePath));
    }

    /**
     * 打分（调用方已按 {@link #normalize(String)} 预归一化的文本）。返回 {@link #NO_MATCH} 表示无命中。
     */
    public int scoreNormalized(String name, String relativePath, String absolutePath) {
        int best = NO_MATCH;
        best = better(best, fieldScore(name, NAME_OFFSET));
        best = better(best, fieldScore(relativePath, RELATIVE_PATH_OFFSET));
        best = better(best, fieldScore(absolutePath, PATH_OFFSET));
        return best;
    }

    private int fieldScore(String text, int offset) {
        int s = scoreFieldNormalized(text);
        return s == NO_MATCH ? NO_MATCH : s + offset;
    }

    private static int better(int current, int candidate) {
        return candidate < current ? candidate : current;
    }

    /**
     * 单字段三级打分（输入需已按当前大小写模式归一化）。返回 {@link #NO_MATCH} 表示不匹配。
     */
    public int scoreFieldNormalized(String text) {
        if (text == null || text.isEmpty()) {
            return NO_MATCH;
        }
        int textLen = text.length();
        int queryLen = query.length();
        if (queryLen > textLen) {
            return NO_MATCH;
        }

        // 前缀档
        int i = 0;
        while (i < queryLen && text.charAt(i) == query.charAt(i)) {
            i++;
        }
        if (i == queryLen) {
            return textLen - queryLen;
        }

        // 子串档：自 i 处继续找首个完整匹配
        int idx = text.indexOf(query, i);
        if (idx >= 0) {
            return SUBSTRING_BASE + idx;
        }

        // 子序列档：贪心最左匹配
        int score = SUBSEQUENCE_BASE;
        int pi = 0;
        int prev = -1;
        for (int t = 0; t < textLen && pi < queryLen; t++) {
            if (text.charAt(t) == query.charAt(pi)) {
                if (prev >= 0) {
                    score += t - prev - 1;
                }
                prev = t;
                pi++;
            }
        }
        if (pi < queryLen) {
            return NO_MATCH;
        }
        return score + (textLen - queryLen);
    }
}